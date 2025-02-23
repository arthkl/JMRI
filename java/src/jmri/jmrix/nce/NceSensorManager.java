package jmri.jmrix.nce;

import java.util.Locale;
import javax.annotation.Nonnull;
import jmri.JmriException;
import jmri.NamedBean;
import jmri.Sensor;
import jmri.jmrix.AbstractMRReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Manage the NCE-specific Sensor implementation.
 * <p>
 * System names are "NSnnn", where N is the user configurable system prefix,
 * nnn is the sensor number without padding.
 * <p>
 * This class is responsible for generating polling messages for the
 * NceTrafficController, see nextAiuPoll()
 *
 * @author Bob Jacobsen Copyright (C) 2003
 */
public class NceSensorManager extends jmri.managers.AbstractSensorManager
        implements NceListener {

    public NceSensorManager(NceSystemConnectionMemo memo) {
        super(memo);
        for (int i = MINAIU; i <= MAXAIU; i++) {
            aiuArray[i] = null;
        }
        listener = new NceListener() {
            @Override
            public void message(NceMessage m) {
            }

            @Override
            public void reply(NceReply r) {
                if (r.isSensorMessage()) {
                    mInstance.handleSensorMessage(r);
                }
            }
        };
        memo.getNceTrafficController().addNceListener(listener);
    }

    private final NceSensorManager mInstance = null;

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public NceSystemConnectionMemo getMemo() {
        return (NceSystemConnectionMemo) memo;
    }

    // to free resources when no longer used
    @Override
    public void dispose() {
        stopPolling = true;  // tell polling thread to go away
        Thread thread = pollThread;
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException ex) {
                log.warn("dispose interrupted");
            }
        }
        getMemo().getNceTrafficController().removeNceListener(listener);
        super.dispose();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Assumes calling method has checked that a Sensor with this system
     * name does not already exist.
     *
     * @throws IllegalArgumentException if the system name is not in a valid format
     */
    @Override
    @Nonnull
    protected Sensor createNewSensor(@Nonnull String systemName, String userName) throws IllegalArgumentException {

        int number = 0;
        String normName;
        try {
            // see if this is a valid address
            String address = systemName.substring(getSystemPrefix().length() + 1);
            normName = createSystemName(address, getSystemPrefix());
            // parse converted system name
            number = Integer.parseInt(normName.substring(getSystemPrefix().length() + 1));
        } catch (NumberFormatException | JmriException e) {
            throw new IllegalArgumentException("Unable to convert " +  // NOI18N
                    systemName.substring(getSystemPrefix().length() + 1) +
                    " to NCE sensor address"); // NOI18N
        }
        Sensor s = new NceSensor(normName);
        s.setUserName(userName);

        // ensure the AIU exists
        int index = (number / 16) + 1;
        if (aiuArray[index] == null) {
            aiuArray[index] = new NceAIU();
            buildActiveAIUs();
        }

        // register this sensor with the AIU
        aiuArray[index].registerSensor(s, number - (index - 1) * 16);

        return s;
    }

    NceAIU[] aiuArray = new NceAIU[MAXAIU + 1];  // element 0 isn't used
    int[] activeAIUs = new int[MAXAIU];  // keep track of those worth polling
    int activeAIUMax = 0;       // last+1 element used of activeAIUs
    private static final int MINAIU = 1;
    private static final int MAXAIU = 63;
    private static final int MAXPIN = 14;    // only pins 1 - 14 used on NCE AIU

    volatile Thread pollThread;
    volatile boolean stopPolling = false;
    NceListener listener;

    // polling parameters and variables
    private final int shortCycleInterval = 200;
    private final int longCycleInterval = 10000;  // when we know async messages are flowing
    private final long maxSilentInterval = 30000;  // max slow poll time without hearing an async message
    private final int pollTimeout = 20000;    // in case of lost response
    private int aiuCycleCount;
    private long lastMessageReceived;     // time of last async message
    private NceAIU currentAIU;
    private boolean awaitingReply = false;
    private boolean awaitingDelay = false;

    /**
     * Build the array of the indices of AIUs which have been polled, and
     * ensures that pollManager has all the information it needs to work
     * correctly.
     *
     */
    /* Some logic notes
     *
     * Sensor polling normally happens on a short cycle - the NCE round-trip
     * response time (normally 50mS, set by the serial line timeout) plus
     * the "shortCycleInterval" defined above. If an async sensor message is received,
     * we switch to the longCycleInterval since really we don't need to poll at all.
     *
     * We use the long poll only if the following conditions are satisified:
     *
     * -- there have been at least two poll cycle completions since the last change
     * to the list of active sensor - this means at least one complete poll cycle,
     * so we are sure we know the states of all the sensors to begin with
     *
     * -- we have received an async message in the last maxSilentInterval, so that
     * if the user turns off async messages (possible, though dumb in mid session)
     * the system will stumble back to life
     *
     * The interaction between buildActiveAIUs and pollManager is designed so that
     * no explicit sync or locking is needed when the former changes the list of active
     * AIUs used by the latter. At worst, there will be one cycle which polls the same
     * sensor twice.
     *
     * Be VERY CAREFUL if you change any of this.
     *
     */
    private void buildActiveAIUs() {
        activeAIUMax = 0;
        for (int a = MINAIU; a <= MAXAIU; ++a) {
            if (aiuArray[a] != null) {
                activeAIUs[activeAIUMax++] = a;
            }
        }
        aiuCycleCount = 0;    // force another polling cycle
        lastMessageReceived = Long.MIN_VALUE;
        if (activeAIUMax > 0) {
            if (pollThread == null) {
                pollThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pollManager();
                    }
                });
                pollThread.setName("NCE Sensor Poll");
                pollThread.setDaemon(true);
                pollThread.start();
            } else {
                synchronized (this) {
                    if (awaitingDelay) {  // interrupt long between-poll wait
                        notify();
                    }
                }
            }
        }
    }

    public NceMessage makeAIUPoll(int aiuNo) {
        // use old 4 byte read command if not USB
        if (getMemo().getNceTrafficController().getUsbSystem() == NceTrafficController.USB_SYSTEM_NONE) {
            return makeAIUPoll4ByteReply(aiuNo);
        } else {
            return makeAIUPoll2ByteReply(aiuNo);
        }
    }

    /**
     * Construct a binary-formatted AIU poll message
     *
     * @param aiuNo number of AIU to poll
     * @return message to be queued
     */
    private NceMessage makeAIUPoll4ByteReply(int aiuNo) {
        NceMessage m = new NceMessage(2);
        m.setBinary(true);
        m.setReplyLen(NceMessage.REPLY_4);
        m.setElement(0, NceMessage.READ_AUI4_CMD);
        m.setElement(1, aiuNo);
        m.setTimeout(pollTimeout);
        return m;
    }

    /**
     * construct a binary-formatted AIU poll message
     *
     * @param aiuNo number of AIU to poll
     * @return message to be queued
     */
    private NceMessage makeAIUPoll2ByteReply(int aiuNo) {
        NceMessage m = new NceMessage(2);
        m.setBinary(true);
        m.setReplyLen(NceMessage.REPLY_2);
        m.setElement(0, NceMessage.READ_AUI2_CMD);
        m.setElement(1, aiuNo);
        m.setTimeout(pollTimeout);
        return m;
    }

    /**
     * Send poll messages for AIU sensors. Also interact with
     * asynchronous sensor state messages. Adjust poll cycle according to
     * whether any async messages have been received recently. Also we require
     * one poll of each sensor before squelching active polls.
     */
    private void pollManager() {
        while (!stopPolling) {
            for (int a = 0; a < activeAIUMax; ++a) {
                int aiuNo = activeAIUs[a];
                currentAIU = aiuArray[aiuNo];
                if (currentAIU != null) {    // in case it has gone away
                    NceMessage m = makeAIUPoll(aiuNo);
                    synchronized (this) {
                        log.debug("queueing poll request for AIU {}", aiuNo);
                        getMemo().getNceTrafficController().sendNceMessage(m, this);
                        awaitingReply = true;
                        try {
                            wait(pollTimeout);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); // retain if needed later
                            return;
                        }
                    }
                    int delay = shortCycleInterval;
                    if (aiuCycleCount >= 2
                            && lastMessageReceived >= System.currentTimeMillis() - maxSilentInterval) {
                        delay = longCycleInterval;
                    }
                    synchronized (this) {
                        if (awaitingReply && !stopPolling) {
                            log.warn("timeout awaiting poll response for AIU {}", aiuNo);
                            // slow down the poll since we're not getting responses
                            // this lets NceConnectionStatus to do its thing
                            delay = pollTimeout;
                        }
                        try {
                            awaitingDelay = true;
                            wait(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); // retain if needed later
                            return;
                        } finally {
                            awaitingDelay = false;
                        }
                    }
                }
            }
            ++aiuCycleCount;
        }
    }

    @Override
    public void message(NceMessage r) {
        log.warn("unexpected message");
    }

    /**
     * Process single received reply from sensor poll.
     */
    @Override
    public void reply(NceReply r) {
        if (!r.isUnsolicited()) {
            int bits;
            synchronized (this) {
                bits = r.pollValue();  // bits is the value in hex from the message
                awaitingReply = false;
                this.notify();
            }
            currentAIU.markChanges(bits);
            if (log.isDebugEnabled()) {
                String str = jmri.util.StringUtil.twoHexFromInt((bits >> 4) & 0xf);
                str += " ";
                str = jmri.util.StringUtil.appendTwoHexFromInt(bits & 0xf, str);
                log.debug("sensor poll reply received: \"{}\"", str);
            }
        }
    }

    /**
     * Handle an unsolicited sensor (AIU) state message.
     *
     * @param r sensor message
     */
    public void handleSensorMessage(AbstractMRReply r) {
        int index = r.getElement(1) - 0x30;
        int indicator = r.getElement(2);
        if (r.getElement(0) == 0x61 && r.getElement(1) >= 0x30 && r.getElement(1) <= 0x6f
                && ((indicator >= 0x41 && indicator <= 0x5e) || (indicator >= 0x61 && indicator <= 0x7e))) {
            lastMessageReceived = System.currentTimeMillis();
            if (aiuArray[index] == null) {
                log.debug("unsolicited message \"{}\" for unused sensor array", r.toString());
            } else {
                int sensorNo;
                int newState;
                if (indicator >= 0x60) {
                    sensorNo = indicator - 0x61;
                    newState = Sensor.ACTIVE;
                } else {
                    sensorNo = indicator - 0x41;
                    newState = Sensor.INACTIVE;
                }
                Sensor s = aiuArray[index].getSensor(sensorNo);
                if (s.getInverted()) {
                    if (newState == Sensor.ACTIVE) {
                        newState = Sensor.INACTIVE;
                    } else if (newState == Sensor.INACTIVE) {
                        newState = Sensor.ACTIVE;
                    }
                }

                if (log.isDebugEnabled()) {
                    log.debug("Handling sensor message \"{}\" for {} {}",
                        r, s.getSystemName(), s.describeState(newState) );
                }
                aiuArray[index].sensorChange(sensorNo, newState);
            }
        } else {
            log.warn("incorrect sensor message: {}", r.toString());
        }
    }

    @Override
    public boolean allowMultipleAdditions(@Nonnull String systemName) {
        return true;
    }

    @Override
    @Nonnull
    public String createSystemName(@Nonnull String curAddress, @Nonnull String prefix) throws JmriException {
        if (curAddress.contains(":")) {
            // Sensor address is presented in the format AIU Cab Address:Pin Number On AIU
            // Should we be validating the values of aiucab address and pin number?
            // Yes we should, added check for valid AIU and pin ranges DBoudreau 2/13/2013
            int seperator = curAddress.indexOf(":");
            try {
                aiucab = Integer.parseInt(curAddress.substring(0, seperator));
                pin = Integer.parseInt(curAddress.substring(seperator + 1));
            } catch (NumberFormatException ex) {
                throw new JmriException("Unable to convert "+curAddress+" into the cab and pin format of nn:xx");
            }
            iName = (aiucab - 1) * 16 + pin - 1;

        } else {
            //Entered in using the old format
            try {
                iName = Integer.parseInt(curAddress);
            } catch (NumberFormatException ex) {
                throw new JmriException("Hardware Address passed "+curAddress+" should be a number or the cab and pin format of nn:xx");
            }
            pin = iName % 16 + 1;
            aiucab = iName / 16 + 1;
        }
        // only pins 1 through 14 are valid
        if (pin == 0 || pin > MAXPIN) {
            throw new JmriException("Sensor pin number "+pin+" for address "+curAddress+" is out of range; only pin numbers 1 - 14 are valid");
        }
        if (aiucab == 0 || aiucab > MAXAIU) {
            throw new JmriException("AIU number "+aiucab+" for address "+curAddress+" is out of range; only AIU 1 - 63 are valid");
        }
        return prefix + typeLetter() + iName;
    }

    int aiucab = 0;
    int pin = 0;
    int iName = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public String validateSystemNameFormat(@Nonnull String name, @Nonnull Locale locale) {
        String parts[];
        int num;
        if (name.contains(":")) {
            parts = super.validateSystemNameFormat(name, locale)
                    .substring(getSystemNamePrefix().length()).split(":");
            if (parts.length != 2) {
                throw new NamedBean.BadSystemNameException(
                        Bundle.getMessage(Locale.ENGLISH, "InvalidSystemNameNeedCabAndPin", name),
                        Bundle.getMessage(locale, "InvalidSystemNameNeedCabAndPin", name));
            }
        } else {
            parts = new String[]{"0", "0"};
            try {
                num = Integer.parseInt(super.validateSystemNameFormat(name, locale)
                        .substring(getSystemNamePrefix().length()));
                parts[0] = Integer.toString((num / 16) + 1); // aiu cab
                parts[1] = Integer.toString((num % 16) + 1); // aiu pin
            } catch (NumberFormatException ex) {
                throw new NamedBean.BadSystemNameException(
                        Bundle.getMessage(Locale.ENGLISH, "InvalidSystemNameNeedCabAndPin", name),
                        Bundle.getMessage(locale, "InvalidSystemNameNeedCabAndPin", name));
            }
        }
        try {
            num = Integer.parseInt(parts[0]);
            if (num < MINAIU || num > MAXAIU) {
                throw new NamedBean.BadSystemNameException(
                        Bundle.getMessage(Locale.ENGLISH, "InvalidSystemNameBadAIUCab", name),
                        Bundle.getMessage(locale, "InvalidSystemNameBadAIUCab", name));
            }
        } catch (NumberFormatException ex) {
            throw new NamedBean.BadSystemNameException(
                    Bundle.getMessage(Locale.ENGLISH, "InvalidSystemNameBadAIUCab", name),
                    Bundle.getMessage(locale, "InvalidSystemNameBadAIUCab", name));
        }
        try {
            num = Integer.parseInt(parts[1]);
            if (num < 1 || num > MAXPIN) {
                throw new NamedBean.BadSystemNameException(
                        Bundle.getMessage(Locale.ENGLISH, "InvalidSystemNameBadAIUPin", name),
                        Bundle.getMessage(locale, "InvalidSystemNameBadAIUPin", name));
            }
        } catch (NumberFormatException ex) {
            throw new NamedBean.BadSystemNameException(
                    Bundle.getMessage(Locale.ENGLISH, "InvalidSystemNameBadAIUCab", name),
                    Bundle.getMessage(locale, "InvalidSystemNameBadAIUCab", name));
        }
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NameValidity validSystemNameFormat(@Nonnull String systemName) {
        if (super.validSystemNameFormat(systemName) == NameValidity.VALID) {
            try {
                validateSystemNameFormat(systemName);
            } catch (IllegalArgumentException ex) {
                if (systemName.endsWith(":")) {
                    try {
                        int num = Integer.parseInt(systemName.substring(getSystemNamePrefix().length(), systemName.length() - 1));
                        if (num >= MINAIU && num <= MAXAIU) {
                            return NameValidity.VALID_AS_PREFIX_ONLY;
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException iex) {
                        // do nothing; will return INVALID
                    }
                }
                return NameValidity.INVALID;
            }
        }
        return NameValidity.VALID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEntryToolTip() {
        return Bundle.getMessage("AddInputEntryToolTip");
    }

    private final static Logger log = LoggerFactory.getLogger(NceSensorManager.class);

}

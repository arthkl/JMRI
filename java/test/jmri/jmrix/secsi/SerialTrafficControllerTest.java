package jmri.jmrix.secsi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import jmri.jmrix.AbstractMRMessage;
import jmri.util.JUnitUtil;

import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 * JUnit tests for the SerialTrafficController class
 *
 * @author Bob Jacobsen Copyright 2005, 2007, 2008
 */
public class SerialTrafficControllerTest extends jmri.jmrix.AbstractMRNodeTrafficControllerTest {

    @Test
    public void testSerialNodeEnumeration() {
        SerialTrafficController c = (SerialTrafficController)tc;
        SerialNode b = new SerialNode(1, SerialNode.DAUGHTER,c);
        SerialNode f = new SerialNode(3, SerialNode.CABDRIVER,c);
        SerialNode d = new SerialNode(2, SerialNode.CABDRIVER,c);
        SerialNode e = new SerialNode(6, SerialNode.DAUGHTER,c);
        Assert.assertEquals("1st Node", b, c.getNode(0));
        Assert.assertEquals("2nd Node", f, c.getNode(1));
        Assert.assertEquals("3rd Node", d, c.getNode(2));
        Assert.assertEquals("4th Node", e, c.getNode(3));
        Assert.assertEquals("no more Nodes", null, c.getNode(4));
        Assert.assertEquals("1st Node Again", b, c.getNode(0));
        Assert.assertEquals("2nd Node Again", f, c.getNode(1));
        Assert.assertEquals("node with address 6", e, c.getNodeFromAddress(6));
        Assert.assertEquals("3rd Node again", d, c.getNode(2));
        Assert.assertEquals("no node with address 0", null, c.getNodeFromAddress(0));
        c.deleteNode(6);
        Assert.assertEquals("1st Node after del", b, c.getNode(0));
        Assert.assertEquals("2nd Node after del", f, c.getNode(1));
        Assert.assertEquals("3rd Node after del", d, c.getNode(2));
        Assert.assertEquals("no more Nodes after del", null, c.getNode(3));
        c.deleteNode(1);
        jmri.util.JUnitAppender.assertWarnMessage("Deleting the serial node active in the polling loop");
        Assert.assertEquals("1st Node after del2", f, c.getNode(0));
        Assert.assertEquals("2nd Node after del2", d, c.getNode(1));
        Assert.assertEquals("no more Nodes after del2", null, c.getNode(2));
    }

    @Test
    @Disabled("Test requires further setup")
    public void testSerialOutput() {
        SerialTrafficController c = (SerialTrafficController)tc;
        SerialNode a = new SerialNode(c);
        Assert.assertNotNull("exists", a);
        SerialNode g = new SerialNode(5, SerialNode.DAUGHTER,c);
        Assert.assertTrue("must Send", g.mustSend());
        g.resetMustSend();
        Assert.assertTrue("must Send off", !(g.mustSend()));
        // c.setSerialOutput("VL5B2", false); // test and 12 year old method removed, called nowhere as of 4.9.4
        AbstractMRMessage m = g.createOutPacket();
        Assert.assertEquals("packet size", 9, m.getNumDataElements());
        Assert.assertEquals("node address", 5, m.getElement(0));
        Assert.assertEquals("byte 1 lo nibble", 0x02, m.getElement(1));
        Assert.assertEquals("byte 1 hi nibble", 0x10, m.getElement(2));
        Assert.assertEquals("byte 2 lo nibble", 0x20, m.getElement(3));
        Assert.assertEquals("byte 2 hi nibble", 0x30, m.getElement(4));
        Assert.assertEquals("byte 3 lo nibble", 0x41, m.getElement(5));
        Assert.assertEquals("byte 3 hi nibble", 0x50, m.getElement(6));
        Assert.assertEquals("byte 4 lo nibble", 0x60, m.getElement(7));
        Assert.assertEquals("byte 4 hi nibble", 0x70, m.getElement(8));
    }

    @Test
    public void testListenerScaffold() {
        SerialListenerScaffold l = new SerialListenerScaffold();
        SerialMessage msg = new SerialMessage(5);
        msg.setOpCode(0x81);
        msg.setElement(1, (byte) 0x02);
        msg.setElement(2, (byte) 0xA2);
        msg.setElement(3, (byte) 0x00);
        
        l.message(msg);
        Assertions.assertTrue( msg == rcvdMsg );
        
        SerialReply reply = new SerialReply("010203");
        l.reply(reply);
        Assertions.assertTrue( reply == rcvdReply );
        
    }

    @Test
    public void testScaffold() throws java.io.IOException {
        SerialPortControllerScaffold scaff = new SerialPortControllerScaffold(memo);
        
        Assertions.assertNotNull(scaff);
        Assertions.assertNotNull(tostream);
        Assertions.assertNotNull(ostream);
        Assertions.assertNotNull(istream);
        Assertions.assertNotNull(tistream);
        
        scaff.dispose();
    }

    // internal class to simulate a Listener
    private class SerialListenerScaffold implements SerialListener {

        SerialListenerScaffold() {
            rcvdReply = null;
            rcvdMsg = null;
        }

        @Override
        public void message(SerialMessage m) {
            rcvdMsg = m;
        }

        @Override
        public void reply(SerialReply r) {
            rcvdReply = r;
        }
    }
    SerialReply rcvdReply;
    SerialMessage rcvdMsg;

    // internal class to simulate a PortController
    private class SerialPortControllerScaffold extends SerialPortController {

        SerialPortControllerScaffold(SecsiSystemConnectionMemo memo) throws java.io.IOException {
            super(memo);
            PipedInputStream tempPipe;
            tempPipe = new PipedInputStream();
            tostream = new DataInputStream(tempPipe);
            ostream = new DataOutputStream(new PipedOutputStream(tempPipe));
            tempPipe = new PipedInputStream();
            istream = new DataInputStream(tempPipe);
            tistream = new DataOutputStream(new PipedOutputStream(tempPipe));
        }

        @Override
        public java.util.Vector<String> getPortNames() {
            return null;
        }

        @Override
        public String openPort(String portName, String appName) {
            return null;
        }

        @Override
        public void configure() {
        }

        @Override
        public String[] validBaudRates() {
            return new String[] {};
        }

        //@Override
        @Override
        public int[] validBaudNumbers() {
            return new int[] {};
        }

        // returns the InputStream from the port
        @Override
        public DataInputStream getInputStream() {
            return istream;
        }

        // returns the outputStream to the port
        @Override
        public DataOutputStream getOutputStream() {
            return ostream;
        }

        // check that this object is ready to operate
        @Override
        public boolean status() {
            return true;
        }

    }

    private DataOutputStream ostream;  // Traffic controller writes to this
    private DataInputStream tostream; // so we can read it from this

    private DataOutputStream tistream; // tests write to this
    private DataInputStream istream;  // so the traffic controller can read from this

    private SecsiSystemConnectionMemo memo;
    
    @Override
    @BeforeEach
    public  void setUp() {
        JUnitUtil.setUp();
        memo = new SecsiSystemConnectionMemo();
        tc = new SerialTrafficController(memo);
    }

    @Override
    @AfterEach
    public  void tearDown() {
        if ( memo != null ) {
            memo.dispose();
            memo = null;
        }
        JUnitUtil.clearShutDownManager(); // put in place because AbstractMRTrafficController implementing subclass was not terminated properly
        JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(SerialTrafficControllerTest.class);

}

package jmri.profile;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import jmri.beans.Bean;
import jmri.jmrit.roster.Roster;
import jmri.util.FileUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage JMRI configuration profiles.
 * <p>
 * This manager, and its configuration, fall outside the control of the
 * {@link jmri.ConfigureManager} since the ConfigureManager's configuration is
 * influenced by this manager.
 *
 * @author Randall Wood
 */
public class ProfileManager extends Bean {

    private final ArrayList<Profile> profiles = new ArrayList<>();
    private final ArrayList<File> searchPaths = new ArrayList<>();
    private Profile activeProfile = null;
    private Profile nextActiveProfile = null;
    private final File catalog;
    private File configFile = null;
    private boolean readingProfiles = false;
    private boolean autoStartActiveProfile = false;
    private File defaultSearchPath = new File(FileUtil.getPreferencesPath());
    private int autoStartActiveProfileTimeout = 10;
    private static ProfileManager instance = null;
    public static final String ACTIVE_PROFILE = "activeProfile"; // NOI18N
    public static final String NEXT_PROFILE = "nextProfile"; // NOI18N
    private static final String AUTO_START = "autoStart"; // NOI18N
    private static final String AUTO_START_TIMEOUT = "autoStartTimeout"; // NOI18N
    private static final String CATALOG = "profiles.xml"; // NOI18N
    private static final String PROFILE = "profile"; // NOI18N
    public static final String PROFILES = "profiles"; // NOI18N
    private static final String PROFILECONFIG = "profileConfig"; // NOI18N
    public static final String SEARCH_PATHS = "searchPaths"; // NOI18N
    public static final String DEFAULT = "default"; // NOI18N
    public static final String DEFAULT_SEARCH_PATH = "defaultSearchPath"; // NOI18N
    public static final String SYSTEM_PROPERTY = "org.jmri.profile"; // NOI18N
    private static final Logger log = LoggerFactory.getLogger(ProfileManager.class);

    /**
     * Create a new ProfileManager using the default catalog. In almost all
     * cases, the use of {@link #getDefault()} is preferred.
     */
    public ProfileManager() {
        this(new File(FileUtil.getPreferencesPath() + CATALOG));
    }

    /**
     * Create a new ProfileManager. In almost all cases, the use of
     * {@link #getDefault()} is preferred.
     *
     * @param catalog
     */
    // TODO: write Test cases using this.
    public ProfileManager(File catalog) {
        this.catalog = catalog;
        try {
            this.readProfiles();
            this.findProfiles();
        } catch (JDOMException | IOException ex) {
            log.error(ex.getLocalizedMessage(), ex);
        }
    }

    /**
     * Get the default {@link ProfileManager}.
     *
     * The default ProfileManager needs to be loaded before the InstanceManager
     * since user interaction with the ProfileManager may change how the
     * InstanceManager is configured.
     *
     * @return the default ProfileManager.
     * @deprecated Use {@link #getDefault() }.
     */
    @Deprecated
    public static ProfileManager defaultManager() {
        return ProfileManager.getDefault();
    }

    /**
     * Get the default {@link ProfileManager}.
     *
     * The default ProfileManager needs to be loaded before the InstanceManager
     * since user interaction with the ProfileManager may change how the
     * InstanceManager is configured.
     *
     * @return the default ProfileManager.
     * @since 3.11.8
     */
    public static ProfileManager getDefault() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    /**
     * Get the {@link Profile} that is currently in use.
     *
     * @return The in use Profile.
     */
    public Profile getActiveProfile() {
        return activeProfile;
    }

    /**
     * Set the {@link Profile} to use. This method finds the Profile by Id and
     * calls {@link #setActiveProfile(jmri.profile.Profile)}.
     *
     * @param id
     */
    public void setActiveProfile(String id) {
        if (id == null) {
            Profile old = activeProfile;
            activeProfile = null;
            FileUtil.setProfilePath(null);
            this.propertyChangeSupport.firePropertyChange(ProfileManager.ACTIVE_PROFILE, old, null);
            log.debug("Setting active profile to null");
            return;
        }
        for (Profile p : profiles) {
            if (p.getId().equals(id)) {
                this.setActiveProfile(p);
                return;
            }
        }
        log.warn("Unable to set active profile. No profile with id {} could be found.", id);
    }

    /**
     * Set the {@link Profile} to use.
     *
     * Once the {@link jmri.ConfigureManager} is loaded, this only sets the
     * Profile used at next application start.
     *
     * @param profile
     */
    public void setActiveProfile(Profile profile) {
        Profile old = activeProfile;
        if (profile == null) {
            activeProfile = null;
            FileUtil.setProfilePath(null);
            this.propertyChangeSupport.firePropertyChange(ProfileManager.ACTIVE_PROFILE, old, null);
            log.debug("Setting active profile to null");
            return;
        }
        activeProfile = profile;
        FileUtil.setProfilePath(profile.getPath().toString());
        this.propertyChangeSupport.firePropertyChange(ProfileManager.ACTIVE_PROFILE, old, profile);
        log.debug("Setting active profile to {}", profile.getId());
    }

    protected Profile getNextActiveProfile() {
        return this.nextActiveProfile;
    }

    protected void setNextActiveProfile(Profile profile) {
        Profile old = this.nextActiveProfile;
        if (profile == null) {
            this.nextActiveProfile = null;
            this.propertyChangeSupport.firePropertyChange(ProfileManager.NEXT_PROFILE, old, null);
            log.debug("Setting next active profile to null");
            return;
        }
        this.nextActiveProfile = profile;
        this.propertyChangeSupport.firePropertyChange(ProfileManager.NEXT_PROFILE, old, profile);
        log.debug("Setting next active profile to {}", profile.getId());
    }

    /**
     * Save the active {@link Profile} and automatic start setting.
     *
     * @throws IOException
     */
    public void saveActiveProfile() throws IOException {
        this.saveActiveProfile(this.getActiveProfile(), this.autoStartActiveProfile);
    }

    protected void saveActiveProfile(Profile profile, boolean autoStart) throws IOException {
        Properties p = new Properties();
        FileOutputStream os = null;

        if (profile != null) {
            p.setProperty(ACTIVE_PROFILE, profile.getId());
            p.setProperty(AUTO_START, Boolean.toString(autoStart));
            p.setProperty(AUTO_START_TIMEOUT, Integer.toString(this.getAutoStartActiveProfileTimeout()));
        }
        if (!this.configFile.exists() && !this.configFile.createNewFile()) {
            throw new IOException("Unable to create file at " + this.getConfigFile().getAbsolutePath()); // NOI18N
        }
        try {
            os = new FileOutputStream(this.getConfigFile());
            p.storeToXML(os, "Active profile configuration (saved at " + (new Date()).toString() + ")"); // NOI18N
            os.close();
        } catch (IOException ex) {
            if (os != null) {
                os.close();
            }
            throw ex;
        }

    }

    /**
     * Read the active {@link Profile} and automatic start setting from the
     * ProfileManager config file.
     *
     * @see #getConfigFile()
     * @see #setConfigFile(java.io.File)
     * @throws IOException
     */
    public void readActiveProfile() throws IOException {
        Properties p = new Properties();
        FileInputStream is = null;
        if (this.configFile.exists()) {
            try {
                is = new FileInputStream(this.getConfigFile());
                p.loadFromXML(is);
                is.close();
            } catch (IOException ex) {
                if (is != null) {
                    is.close();
                }
                throw ex;
            }
            this.setActiveProfile(p.getProperty(ACTIVE_PROFILE));
            if (p.containsKey(AUTO_START)) {
                this.setAutoStartActiveProfile(Boolean.parseBoolean(p.getProperty(AUTO_START)));
            }
            if (p.containsKey(AUTO_START_TIMEOUT)) {
                this.setAutoStartActiveProfileTimeout(Integer.parseInt(p.getProperty(AUTO_START_TIMEOUT)));
            }
        }
    }

    /**
     * Get an array of enabled {@link Profile} objects.
     *
     * @return The enabled Profile objects
     */
    public Profile[] getProfiles() {
        return profiles.toArray(new Profile[profiles.size()]);
    }

    /**
     * Get an ArrayList of {@link Profile} objects.
     *
     * @return A list of all Profile objects
     */
    public ArrayList<Profile> getAllProfiles() {
        return new ArrayList<>(profiles);
    }

    /**
     * Get the enabled {@link Profile} at index.
     *
     * @param index
     * @return A Profile
     */
    public Profile getProfiles(int index) {
        if (index >= 0 && index < profiles.size()) {
            return profiles.get(index);
        }
        return null;
    }

    /**
     * Set the enabled {@link Profile} at index.
     *
     * @param profile
     * @param index
     */
    public void setProfiles(Profile profile, int index) {
        Profile oldProfile = profiles.get(index);
        if (!this.readingProfiles) {
            profiles.set(index, profile);
            this.propertyChangeSupport.fireIndexedPropertyChange(PROFILES, index, oldProfile, profile);
        }
    }

    protected void addProfile(Profile profile) {
        if (!profiles.contains(profile)) {
            profiles.add(profile);
            if (!this.readingProfiles) {
                int index = profiles.indexOf(profile);
                this.propertyChangeSupport.fireIndexedPropertyChange(PROFILES, index, null, profile);
                try {
                    this.writeProfiles();
                } catch (IOException ex) {
                    log.warn("Unable to write profiles while adding profile {}.", profile.getId(), ex);
                }
            }
        }
    }

    protected void removeProfile(Profile profile) {
        try {
            int index = profiles.indexOf(profile);
            if (index >= 0) {
                if (profiles.remove(profile)) {
                    this.propertyChangeSupport.fireIndexedPropertyChange(PROFILES, index, profile, null);
                    this.writeProfiles();
                }
                if (profile.equals(this.getNextActiveProfile())) {
                    this.setNextActiveProfile(null);
                    this.saveActiveProfile(this.getActiveProfile(), this.autoStartActiveProfile);
                }
            }
        } catch (IOException ex) {
            log.warn("Unable to write profiles while removing profile {}.", profile.getId(), ex);
        }
    }

    /**
     * Get the paths that are searched for Profiles when presenting the user
     * with a list of Profiles. Profiles that are discovered in these paths are
     * automatically added to the catalog.
     *
     * @return Paths that may contain profiles.
     */
    public File[] getSearchPaths() {
        return searchPaths.toArray(new File[searchPaths.size()]);
    }

    public ArrayList<File> getAllSearchPaths() {
        return this.searchPaths;
    }

    /**
     * Get the search path at index.
     *
     * @param index
     * @return A path that may contain profiles.
     */
    public File getSearchPaths(int index) {
        if (index >= 0 && index < searchPaths.size()) {
            return searchPaths.get(index);
        }
        return null;
    }

    protected void addSearchPath(File path) throws IOException {
        if (!searchPaths.contains(path)) {
            searchPaths.add(path);
            if (!this.readingProfiles) {
                int index = searchPaths.indexOf(path);
                this.propertyChangeSupport.fireIndexedPropertyChange(SEARCH_PATHS, index, null, path);
                this.writeProfiles();
            }
            this.findProfiles(path);
        }
    }

    protected void removeSearchPath(File path) throws IOException {
        if (searchPaths.contains(path)) {
            int index = searchPaths.indexOf(path);
            searchPaths.remove(path);
            this.propertyChangeSupport.fireIndexedPropertyChange(SEARCH_PATHS, index, path, null);
            this.writeProfiles();
            if (this.getDefaultSearchPath().equals(path)) {
                this.setDefaultSearchPath(new File(FileUtil.getPreferencesPath()));
            }
        }
    }

    @Nonnull
    protected File getDefaultSearchPath() {
        return this.defaultSearchPath;
    }

    protected void setDefaultSearchPath(@Nonnull File defaultSearchPath) throws IOException {
        if (defaultSearchPath == null) {
            throw new NullPointerException();
        }
        if (!defaultSearchPath.equals(this.defaultSearchPath)) {
            File oldDefault = this.defaultSearchPath;
            this.defaultSearchPath = defaultSearchPath;
            this.propertyChangeSupport.firePropertyChange(DEFAULT_SEARCH_PATH, oldDefault, this.defaultSearchPath);
            this.writeProfiles();
        }
    }

    private void readProfiles() throws JDOMException, IOException {
        try {
            boolean reWrite = false;
            if (!catalog.exists()) {
                this.writeProfiles();
            }
            if (!catalog.canRead()) {
                return;
            }
            this.readingProfiles = true;
            Document doc = (new SAXBuilder()).build(catalog);
            profiles.clear();

            for (Element e : doc.getRootElement().getChild(PROFILES).getChildren()) {
                File pp = FileUtil.getFile(e.getAttributeValue(Profile.PATH));
                try {
                    Profile p = new Profile(pp);
                    this.addProfile(p);
                } catch (FileNotFoundException ex) {
                    log.info("Cataloged profile \"{}\" not in expected location\nSearching for it in {}", e.getAttributeValue(Profile.ID), pp.getParentFile());
                    this.findProfiles(pp.getParentFile());
                    reWrite = true;
                }
            }
            searchPaths.clear();
            for (Element e : doc.getRootElement().getChild(SEARCH_PATHS).getChildren()) {
                File path = FileUtil.getFile(e.getAttributeValue(Profile.PATH));
                if (!searchPaths.contains(path)) {
                    this.addSearchPath(path);
                }
                if (Boolean.parseBoolean(e.getAttributeValue(DEFAULT))) {
                    this.defaultSearchPath = path;
                }
            }
            if (searchPaths.isEmpty()) {
                this.addSearchPath(FileUtil.getFile(FileUtil.getPreferencesPath()));
            }
            this.readingProfiles = false;
            if (reWrite) {
                this.writeProfiles();
            }
        } catch (JDOMException | IOException ex) {
            this.readingProfiles = false;
            throw ex;
        }
    }

    private void writeProfiles() throws IOException {
        FileWriter fw = null;
        Document doc = new Document();
        doc.setRootElement(new Element(PROFILECONFIG));
        Element profilesElement = new Element(PROFILES);
        Element pathsElement = new Element(SEARCH_PATHS);
        for (Profile p : this.profiles) {
            Element e = new Element(PROFILE);
            e.setAttribute(Profile.ID, p.getId());
            e.setAttribute(Profile.PATH, FileUtil.getPortableFilename(p.getPath(), true, true));
            profilesElement.addContent(e);
        }
        for (File f : this.searchPaths) {
            Element e = new Element(Profile.PATH);
            e.setAttribute(Profile.PATH, FileUtil.getPortableFilename(f.getPath(), true, true));
            e.setAttribute(DEFAULT, Boolean.toString(f.equals(this.defaultSearchPath)));
            pathsElement.addContent(e);
        }
        doc.getRootElement().addContent(profilesElement);
        doc.getRootElement().addContent(pathsElement);
        try {
            fw = new FileWriter(catalog);
            XMLOutputter fmt = new XMLOutputter();
            fmt.setFormat(Format.getPrettyFormat()
                    .setLineSeparator(System.getProperty("line.separator"))
                    .setTextMode(Format.TextMode.PRESERVE));
            fmt.output(doc, fw);
            fw.close();
        } catch (IOException ex) {
            // close fw if possible
            if (fw != null) {
                fw.close();
            }
            // rethrow the error
            throw ex;
        }
    }

    private void findProfiles() {
        this.searchPaths.stream().forEach((searchPath) -> {
            this.findProfiles(searchPath);
        });
    }

    private void findProfiles(File searchPath) {
        File[] profilePaths = searchPath.listFiles((File pathname) -> Profile.isProfile(pathname));
        if (profilePaths == null) {
            log.error("There was an error reading directory {}.", searchPath.getPath());
            return;
        }
        for (File pp : profilePaths) {
            try {
                Profile p = new Profile(pp);
                this.addProfile(p);
            } catch (IOException ex) {
                log.error("Error attempting to read Profile at {}", pp, ex);
            }
        }
    }

    /**
     * Get the file used to configure the ProfileManager.
     *
     * @return the appConfigFile
     */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * Set the file used to configure the ProfileManager. This is set on a
     * per-application basis.
     *
     * @param configFile the appConfigFile to set
     */
    public void setConfigFile(File configFile) {
        this.configFile = configFile;
        log.debug("Using config file {}", configFile);
    }

    /**
     * Should the app automatically start with the active {@link Profile}
     * without offering the user an opportunity to change the Profile?
     *
     * @return true if the app should start without user interaction
     */
    public boolean isAutoStartActiveProfile() {
        return (this.getActiveProfile() != null && autoStartActiveProfile);
    }

    /**
     * Set if the app will next start without offering the user an opportunity
     * to change the {@link Profile}.
     *
     * @param autoStartActiveProfile the autoStartActiveProfile to set
     */
    public void setAutoStartActiveProfile(boolean autoStartActiveProfile) {
        this.autoStartActiveProfile = autoStartActiveProfile;
    }

    /**
     * Create a default profile if no profiles exist.
     *
     * @return A new profile or null if profiles already exist.
     * @throws IOException
     */
    public Profile createDefaultProfile() throws IllegalArgumentException, IOException {
        if (this.getAllProfiles().isEmpty()) {
            String pn = Bundle.getMessage("defaultProfileName");
            String pid = FileUtil.sanitizeFilename(pn);
            File pp = new File(FileUtil.getPreferencesPath() + pid);
            Profile profile = new Profile(pn, pid, pp);
            this.addProfile(profile);
            this.setAutoStartActiveProfile(true);
            log.info("Created default profile \"{}\"", pn);
            return profile;
        } else {
            return null;
        }
    }

    /**
     * Copy a JMRI configuration not in a profile and its user preferences to a
     * profile.
     *
     * @param config
     * @param name
     * @return The profile with the migrated configuration.
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public Profile migrateConfigToProfile(File config, String name) throws IllegalArgumentException, IOException {
        String pid = FileUtil.sanitizeFilename(name);
        File pp = new File(FileUtil.getPreferencesPath(), pid);
        Profile profile = new Profile(name, pid, pp);
        FileUtil.copy(config, new File(profile.getPath(), Profile.CONFIG_FILENAME));
        FileUtil.copy(new File(config.getParentFile(), "UserPrefs" + config.getName()), new File(profile.getPath(), "UserPrefs" + Profile.CONFIG_FILENAME)); // NOI18N
        this.addProfile(profile);
        log.info("Migrated \"{}\" config to profile \"{}\"", name, name);
        return profile;
    }

    /**
     * Migrate a JMRI application to using {@link Profile}s.
     *
     * Migration occurs when no profile configuration exists, but an application
     * configuration exists. This method also handles the situation where an
     * entirely new user is first starting JMRI, or where a user has deleted all
     * their profiles.
     * <p>
     * When a JMRI application is starting there are eight potential
     * Profile-related states requiring preparation to use profiles:
     * <table>
     * <tr><th>Profile Catalog</th><th>Profile Config</th><th>App
     * Config</th><th>Action</th></tr>
     * <tr><td>YES</td><td>YES</td><td>YES</td><td>No preparation required -
     * migration from earlier JMRI complete</td></tr>
     * <tr><td>YES</td><td>YES</td><td>NO</td><td>No preparation required - JMRI
     * installed after profiles feature introduced</td></tr>
     * <tr><td>YES</td><td>NO</td><td>YES</td><td>Migration required - other
     * JMRI applications migrated to profiles by this user, but not this
     * one</td></tr>
     * <tr><td>YES</td><td>NO</td><td>NO</td><td>No preparation required -
     * prompt user for desired profile if multiple profiles exist, use default
     * otherwise</td></tr>
     * <tr><td>NO</td><td>NO</td><td>NO</td><td>New user - create and use
     * default profile</td></tr>
     * <tr><td>NO</td><td>NO</td><td>YES</td><td>Migration required - need to
     * create first profile</td></tr>
     * <tr><td>NO</td><td>YES</td><td>YES</td><td>No preparation required -
     * catalog will be automatically regenerated</td></tr>
     * <tr><td>NO</td><td>YES</td><td>NO</td><td>No preparation required -
     * catalog will be automatically regenerated</td></tr>
     * </table>
     * This method returns true if a migration occurred, and false in all other
     * circumstances.
     *
     * @param configFilename
     * @return true if a user's existing config was migrated, false otherwise
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public boolean migrateToProfiles(String configFilename) throws IllegalArgumentException, IOException {
        File appConfigFile = new File(configFilename);
        boolean didMigrate = false;
        if (!appConfigFile.isAbsolute()) {
            appConfigFile = new File(FileUtil.getPreferencesPath() + configFilename);
        }
        if (this.getAllProfiles().isEmpty()) { // no catalog and no profile config
            if (!appConfigFile.exists()) { // no catalog and no profile config and no app config: new user
                this.setActiveProfile(this.createDefaultProfile());
                this.saveActiveProfile();
            } else { // no catalog and no profile config, but an existing app config: migrate user who never used profiles before
                this.setActiveProfile(this.migrateConfigToProfile(appConfigFile, jmri.Application.getApplicationName()));
                this.saveActiveProfile();
                didMigrate = true;
            }
        } else if (appConfigFile.exists()) { // catalog and existing app config, but no profile config: migrate user who used profile with other JMRI app
            try {
                this.setActiveProfile(this.migrateConfigToProfile(appConfigFile, jmri.Application.getApplicationName()));
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage().startsWith("A profile already exists at ")) {
                    // caused by attempt to migrate application with custom launcher
                    // strip ".xml" from configFilename name and use that to create profile
                    this.setActiveProfile(this.migrateConfigToProfile(appConfigFile, appConfigFile.getName().substring(0, appConfigFile.getName().length() - 4)));
                } else {
                    // throw the exception so it can be dealt with, since other causes need user attention
                    // (most likely cause is a read-only settings directory)
                    throw ex;
                }
            }
            this.saveActiveProfile();
            didMigrate = true;
        } // all other cases need no prep
        return didMigrate;
    }

    /**
     * Export the {@link jmri.profile.Profile} to a JAR file.
     *
     * @param profile                 The profile to export
     * @param target                  The file to export the profile into
     * @param exportExternalUserFiles If the User Files are not within the
     *                                profile directory, should they be
     *                                included?
     * @param exportExternalRoster    It the roster is not within the profile
     *                                directory, should it be included?
     * @throws IOException
     * @throws org.jdom2.JDOMException
     */
    public void export(Profile profile, File target, boolean exportExternalUserFiles, boolean exportExternalRoster) throws IOException, JDOMException {
        if (!target.exists()) {
            target.createNewFile();
        }
        String tempDirPath = System.getProperty("java.io.tmpdir") + File.separator + "JMRI" + System.currentTimeMillis(); // NOI18N
        FileUtil.createDirectory(tempDirPath);
        File tempDir = new File(tempDirPath);
        File tempProfilePath = new File(tempDir, profile.getPath().getName());
        FileUtil.copy(profile.getPath(), tempProfilePath);
        File config = new File(tempProfilePath, "ProfileConfig.xml"); // NOI18N
        Document doc = (new SAXBuilder()).build(config);
        if (exportExternalUserFiles) {
            FileUtil.copy(new File(FileUtil.getUserFilesPath()), tempProfilePath);
            Element fileLocations = doc.getRootElement().getChild("fileLocations"); // NOI18N
            for (Element fl : fileLocations.getChildren()) {
                if (fl.getAttribute("defaultUserLocation") != null) { // NOI18N
                    fl.setAttribute("defaultUserLocation", "profile:"); // NOI18N
                }
            }
        }
        if (exportExternalRoster) {
            FileUtil.copy(new File(Roster.getDefault().getRosterIndexPath()), new File(tempProfilePath, "roster.xml")); // NOI18N
            FileUtil.copy(new File(Roster.getDefault().getRosterLocation(), "roster"), new File(tempProfilePath, "roster")); // NOI18N
            Element roster = doc.getRootElement().getChild("roster"); // NOI18N
            roster.removeAttribute("directory"); // NOI18N
        }
        if (exportExternalUserFiles || exportExternalRoster) {
            try (FileWriter fw = new FileWriter(config)) {
                XMLOutputter fmt = new XMLOutputter();
                fmt.setFormat(Format.getPrettyFormat()
                        .setLineSeparator(System.getProperty("line.separator"))
                        .setTextMode(Format.TextMode.PRESERVE));
                fmt.output(doc, fw);
            }
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            ZipOutputStream zip = new ZipOutputStream(out);
            this.exportDirectory(zip, tempProfilePath, tempProfilePath.getPath());
            zip.close();
        }
        FileUtil.delete(tempDir);
    }

    private void exportDirectory(ZipOutputStream zip, File source, String root) throws IOException {
        for (File file : source.listFiles()) {
            if (file.isDirectory()) {
                if (!Profile.isProfile(file)) {
                    ZipEntry entry = new ZipEntry(this.relativeName(file, root));
                    entry.setTime(file.lastModified());
                    zip.putNextEntry(entry);
                    this.exportDirectory(zip, file, root);
                }
                continue;
            }
            this.exportFile(zip, file, root);
        }
    }

    private void exportFile(ZipOutputStream zip, File source, String root) throws IOException {
        byte[] buffer = new byte[1024];
        int length;

        try (FileInputStream input = new FileInputStream(source)) {
            ZipEntry entry = new ZipEntry(this.relativeName(source, root));
            entry.setTime(source.lastModified());
            zip.putNextEntry(entry);
            while ((length = input.read(buffer)) > 0) {
                zip.write(buffer, 0, length);
            }
            zip.closeEntry();
        }
    }

    private String relativeName(File file, String root) {
        String path = file.getPath();
        if (path.startsWith(root)) {
            path = path.substring(root.length());
        }
        if (file.isDirectory() && !path.endsWith("/")) {
            path = path + "/";
        }
        return path.replaceAll(File.separator, "/");
    }

    /**
     * Get the active profile.
     *
     * This method initiates the process of setting the active profile when a
     * headless app launches.
     *
     * @return The active {@link Profile}
     * @throws IOException
     * @see ProfileManagerDialog#getStartingProfile(java.awt.Frame)
     */
    public static Profile getStartingProfile() throws IOException {
        if (ProfileManager.getDefault().getActiveProfile() == null) {
            ProfileManager.getDefault().readActiveProfile();
            // Automatically start with only profile if only one profile
            if (ProfileManager.getDefault().getProfiles().length == 1) {
                ProfileManager.getDefault().setActiveProfile(ProfileManager.getDefault().getProfiles(0));
                // Display profile selector if user did not choose to auto start with last used profile
            } else if (!ProfileManager.getDefault().isAutoStartActiveProfile()) {
                return null;
            }
        }
        return ProfileManager.getDefault().getActiveProfile();
    }

    /**
     * Generate a reasonably pseudorandom unique id.
     * <p>
     * This can be used to generate the id for a
     * {@link jmri.profile.NullProfile}. Implementing applications should save
     * this value so that the id of a NullProfile is consistent across
     * application launches.
     *
     * @return String of alphanumeric characters.
     */
    public static String createUniqueId() {
        return Integer.toHexString(Float.floatToIntBits((float) Math.random()));
    }

    void profileNameChange(Profile profile, String oldName) {
        this.propertyChangeSupport.firePropertyChange(new PropertyChangeEvent(profile, Profile.NAME, oldName, profile.getName()));
    }

    /**
     * Seconds to display profile selector before automatically starting.
     *
     * If 0, selector will not automatically dismiss.
     *
     * @return Seconds to display selector.
     */
    public int getAutoStartActiveProfileTimeout() {
        return this.autoStartActiveProfileTimeout;
    }

    /**
     * Set the number of seconds to display the profile selector before
     * automatically starting.
     *
     * If negative or greater than 300 (5 minutes), set to 0 to prevent
     * automatically starting with any profile.
     *
     * Call {@link #saveActiveProfile() } after setting this to persist the
     * value across application restarts.
     *
     * @param autoStartActiveProfileTimeout Seconds to display profile selector
     */
    public void setAutoStartActiveProfileTimeout(int autoStartActiveProfileTimeout) {
        int old = this.autoStartActiveProfileTimeout;
        if (autoStartActiveProfileTimeout < 0 || autoStartActiveProfileTimeout > 500) {
            autoStartActiveProfileTimeout = 0;
        }
        if (old != autoStartActiveProfileTimeout) {
            this.autoStartActiveProfileTimeout = autoStartActiveProfileTimeout;
            this.propertyChangeSupport.firePropertyChange(AUTO_START_TIMEOUT, old, this.autoStartActiveProfileTimeout);
        }
    }
}

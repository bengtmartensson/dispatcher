/*
Copyright (C) 2014,2015 Bengt Martensson.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
*/

package org.harctoolbox.dispatcher;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.harctoolbox.harchardware.HarcHardwareException;
import org.harctoolbox.harchardware.ICommandLineDevice;
import org.harctoolbox.harchardware.Utils;
import org.harctoolbox.harchardware.comm.LocalSerialPort;
import org.harctoolbox.harchardware.comm.LocalSerialPortBuffered;
import org.harctoolbox.harchardware.comm.TcpSocketPort;
import org.harctoolbox.irp.IrpUtils;
import org.harctoolbox.xml.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Dispatcher {
    private final static String lineEnd = System.getProperty("line.separator");

    static class MyFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            StringBuilder str = new StringBuilder(
            "[" + formatDate(record.getMillis()) + "] "
                    + record.getLevel() + ": "
                    + formatMessage(record)
                    + lineEnd);
            Throwable throwable = record.getThrown();
            if (throwable != null)
                str.append(throwable).append(" ").append(throwable.getMessage()).append(lineEnd);
            return str.toString();
        }

        private String formatDate(long millisecs) {
            return (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date(millisecs));
        }
    }

    static class IrCommand {
        String protocol;
        int D;
        int S;
        int F;

        IrCommand(String protocol, int D, int S, int F) {
            this.protocol = protocol.toLowerCase(Locale.US);
            this.D = D;
            this.S = S;
            this.F = F;
        }

        IrCommand(String protocol, int D, int F) {
            this.protocol = protocol.toLowerCase(Locale.US);
            this.D = D;
            S = (protocol.toLowerCase(Locale.US).equals("nec1")) ? 255 - D : -1;
            this.F = F;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final IrCommand other = (IrCommand) obj;
            return this.protocol.equals(other.protocol)
                    && this.D == other.D
                    && this.S == other.S
                    && this.F == other.F;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 89 * hash + Objects.hashCode(this.protocol);
            hash = 89 * hash + this.D;
            hash = 89 * hash + this.S;
            hash = 89 * hash + this.F;
            return hash;
        }

        @Override
        public String toString() {
            return protocol + ":" + D + "_" + (S == -1 ? "" : (S + "_")) + F;
        }
    }

    private boolean verbose = false;
    private boolean girs;
    private boolean stopRequested = false;
    private boolean restartRequested = false;
    private final ICommandLineDevice hardware;
    private Date lastTimeAlive;
    private final int timeout; // seconds

    private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());

    private Map<IrCommand, List<Action>> map = new HashMap<>(32);
    private Map<String, Element> actionRefs = new HashMap<>(32);

    /**
     * Set verbosity.
     * @param verbose
     */
    public void setVerbosity(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Causes the listener to be stopped.
     */
    public void requestStop() {
        stopRequested = true;
    }

    /**
     * Causes the listener to be stopped and subsequently restarted.
     */
    public void requestRestart() {
        stopRequested = true;
        restartRequested = true;
    }

    private int seconds2microseconds(int secs) {
        return 1000*secs;
    }

    private void parseDoc(Document doc) {
        NodeList actionTemplates = doc.getElementsByTagName("action-template");
        for (int i = 0; i < actionTemplates.getLength(); i++) {
            Element e = (Element) actionTemplates.item(i);
            actionRefs.put(e.getAttribute("id"), e);
        }

        NodeList irCommands = doc.getElementsByTagName("ir-command");
        for (int i = 0; i < irCommands.getLength(); i++) {
            Element el = (Element) irCommands.item(i);
            String protocol = el.getAttribute("protocol");
            int D = Integer.parseInt(el.getAttribute("D"));
            int F = Integer.parseInt(el.getAttribute("F"));
            if (F == 999)
                continue;
            String Sattr = el.getAttribute("S");
            IrCommand irCommand = Sattr.isEmpty()
                    ? new IrCommand(protocol, D, F)
                    : new IrCommand(protocol, D, Integer.parseInt(Sattr), F);
            NodeList nl = el.getChildNodes();
            List<Action> actions = new ArrayList<>(32);
            for (int j = 0; j < nl.getLength(); j++) {
                if (nl.item(j).getNodeType() == Node.ELEMENT_NODE) {
                    Element actionEl = (Element) nl.item(j);
                    String param[] = new String[0];
                    if (actionEl.getTagName().equals("actionref")) {
                        Element substActionEl = actionRefs.get(actionEl.getAttribute("template"));
                        NodeList paramNodes = substActionEl.getElementsByTagName("parameter");
                        param = new String[paramNodes.getLength()];
                        for (int k = 0; k < paramNodes.getLength(); k++) {
                            String attributeK = actionEl.getAttribute("arg" + Integer.toString(k+1));
                            param[k] = attributeK.isEmpty() ? ((Element) paramNodes.item(k)).getAttribute("default") : attributeK;
                        }
                        actionEl = (Element) substActionEl.getElementsByTagName("action").item(0);
                    }
                    Action action = new Action(actionEl, param, this);
                    actions.add(action);
                }
            }
            map.put(irCommand, actions);
        }
    }

    /**
     * Constructor for Dispatcher.
     * @param doc Event-action map
     * @param hardware Hardware to listen  to (ICommandLineDevice)
     * @param timeout Timeout in seconds, to restart the listener.
     * @throws IOException
     */
    public Dispatcher(Document doc, ICommandLineDevice hardware, boolean girs, int timeout) throws IOException {
        parseDoc(doc);
        this.hardware = hardware;
        this.timeout = timeout;
        this.girs = girs;
    }

    /**
     * Constructor for Dispatcher.
     * @param configFile Event-action map
     * @param hardware Hardware to listen  to (ICommandLineDevice)
     * @param timeout Timeout in seconds, to restart the listener.
     * @throws IOException
     * @throws org.xml.sax.SAXException Parse problem is configFile.
     */
    public Dispatcher(File configFile, ICommandLineDevice hardware, boolean girs, int timeout) throws IOException, SAXException {
        this(XmlUtils.openXmlFile(configFile, readSchemaFromUrl(Dispatcher.class.getResource("/schemas/event-action-map.xsd")),
                true, true), hardware, girs, timeout);
    }

    private static Schema readSchemaFromUrl(URL schemaFile) throws SAXException {
        return (SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)).newSchema(schemaFile);
    }

    /**
     * Listener loop.
     *
     * @return True if restart is requested.
     * @throws HarcHardwareException
     * @throws IOException
     */
    @SuppressWarnings("SleepWhileInLoop")
    public boolean listen() throws HarcHardwareException, IOException {
        restartRequested = false;
        stopRequested = false;
        if (LocalSerialPortBuffered.class.isInstance(hardware)) {
            LocalSerialPortBuffered lspb = (LocalSerialPortBuffered) hardware;
            lspb.open();
            logger.log(Level.INFO, "Opened {0}", new Object[]{lspb.getPortName()});
            lspb.dropDTR(500); // Force reset of Arduino
        } else
            hardware.open();

        if (girs) {

        } else {
            logger.info("Listen started");
        }
        lastTimeAlive = new Date();
        int noTransmissions = 0;
        IrCommand old = null;
        @SuppressWarnings("UnusedAssignment")
        IrCommand irCommand = null;
        try {
            while (!stopRequested) {
                if (!hardware.ready()) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ex) {
                    }
                    if (!hardware.ready()) {
                        if (old != null)
                            triggerAction(old, noTransmissions);
                        old = null;
                        noTransmissions = 0;
                    }
                }
                String line = hardware.readString(); // does not block
                if (line != null && !line.isEmpty()) {
                    logger.log(Level.FINEST, "\"{0}\"", line);
                    lastTimeAlive = new Date();
                } else {
                    if (timeout > 0
                            && (int) ((new Date()).getTime() - lastTimeAlive.getTime()) > seconds2microseconds(timeout)) {
                        logger.warning("Hardware is pining for the fjords, restarting.");
                        restartRequested = true;
                        stopRequested = true;
                    }
                }

                if (line == null || line.isEmpty() || line.equals("undecoded") || line.equals(".") || line.equals(":"))
                    irCommand = null;
                else {
                    String[] parts = line.trim().split("\\s+");
                    String protocol = parts[0].toLowerCase(Locale.US);
                    if (protocol.equals("nec1") && parts[1].equals("ditto")) {
                        irCommand = old;
                    } else {
                        try {
                            int D = Integer.parseInt(parts[1]);
                            int S = -1;
                            @SuppressWarnings("UnusedAssignment")
                            int F = 0;
                            if (protocol.equals("nec1")) {
                                if (parts.length > 3) {
                                    S = Integer.parseInt(parts[2]);
                                    F = Integer.parseInt(parts[3]);
                                } else {
                                    S = 255 - D;
                                    F = Integer.parseInt(parts[2]);
                                }
                            } else {
                                F = Integer.parseInt(parts[2]);
                            }
                            irCommand = new IrCommand(protocol, D, S, F);
                        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                            logger.log(Level.INFO, "Unparsable entry: \"{0}\"", line);
                            irCommand = null;
                        }
                    }
                }

                if (irCommand != null && irCommand.equals(old)) {
                    noTransmissions++;
                } else {
                    if (old != null)
                        triggerAction(old, noTransmissions);
                    old = irCommand;
                    noTransmissions = 1;
                }
                if (irCommand != null)
                    logger.finest(irCommand.toString());
            }
            if (LocalSerialPortBuffered.class.isInstance(hardware)) {
                LocalSerialPortBuffered lspb = (LocalSerialPortBuffered) hardware;
                logger.log(Level.INFO, "Closing {0}", lspb.getPortName());
            }
            hardware.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "IOException", ex);
        }
        logger.info("Listen ended");
        if (restartRequested)
            logger.info("Requesting restart");
        return restartRequested;
    }

    private void triggerAction(IrCommand irCommand, int noTransmissions) {
        logger.log(Level.FINE, "triggerAction: {0} {1}", new Object[]{irCommand, noTransmissions});
        List<Action> restrictions = map.get(irCommand);
        int noActions = 0;
        if (restrictions != null) {
            for (Action restriction : restrictions) {
                if (noTransmissions >= restriction.getMin() && noTransmissions <= restriction.getMax()) {
                    executeActions(restriction.getActions(), irCommand.toString());
                    noActions++;
                }
            }
        }
        if (noActions == 0)
            logger.log(Level.FINE, "triggerAction: {0} {1} triggered no actions.", new Object[]{irCommand, noTransmissions});
    }

    private void executeActions(List<AbstractAction> actions, String triggerName) {
        for (AbstractAction action : actions) {
            logger.log(Level.INFO, "{0} -> {1}", new Object[]{triggerName, action});
            try {
                boolean success = action.action();
                if (success)
                    logger.log(Level.FINE, "Action {0} succeeded", action.toString());
                else
                    logger.log(Level.WARNING, "Action {0} failed", action.toString());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Action {0} threw IOException: {1}",
                        new Object[]{action.toString(), ex.getMessage()});
            }
        }
    }

    private static void usage(int exitcode) {
        argumentParser.usage();

        //(exitcode == IrpUtils.exitSuccess ? System.out : System.err).println(str);
        doExit(exitcode);
    }

    private static void doExit(int exitcode) {
        System.exit(exitcode);
    }

    @SuppressWarnings("FieldMayBeFinal")
    private final static class CommandLineArgs {
        @Parameter(names = {"-a", "--applicationhome", "--apphome", "-H", "--home"}, description = "Set application home (where files are located)")
        private String applicationHome = null;

        @Parameter(names = {"-b", "--baud"}, description = "Baudrate of serial port")
        private int baud = 115200;

        @Parameter(names = {"-c", "--config"}, description = "Path to the configuration file")
        private String configFilename = "src/main/config/listener.xml"; // FIXME

        @Parameter(names = {"-d", "--device"}, description = "Device name, e.g. COM7: or /dev/ttyUSB0")
        private String device = null;

        @Parameter(names = {"-g", "--girs"}, description = "The device to connect is a Girs server")
        private boolean girs = false;

        @Parameter(names = {"-h", "--help", "-?"}, description = "Display help message")
        private boolean helpRequested = false;

        @Parameter(names = {"-i", "--ip"}, description = "IP address or name")
        private String ip = null;

        @Parameter(names = {"-l", "--logfile"}, description = "Logfile")
        private String logfile = null;

        @Parameter(names = {"-L", "--loglevel"}, description = "Loglevel (ALL, FINEST, FINE, INFO, SEVERE, WARNING, OFF,...)")
        private String loglevel = "INFO";

        @Parameter(names = {"-m", "--maxtries"}, description = "Number of times to try reopening the device")
        private int maxTries = 10;

        @Parameter(names = {"-p", "--port"}, description = "Port number")
        private int port = 33333;

        @Parameter(names = {"-t", "--timeout"}, description = "Timeout for device, in seconds")
        private int timeout = 30;

        @Parameter(names = {"-V", "--version"}, description = "Display version information")
        private boolean versionRequested;

        @Parameter(names = {"-v", "--verbose"}, description = "Execute commands verbosely")
        private boolean verbose;

        @Parameter(names = {"-w", "--wait"}, description = "Time in seconds between reopening attempts")
        private int waitTime = 10;
    }

    private static JCommander argumentParser;
    private static CommandLineArgs commandLineArgs = new CommandLineArgs();

    /**
     * Main function for the program.
     * @param args
     */
    @SuppressWarnings({"UseOfSystemOutOrSystemErr", "SleepWhileInLoop", "null"})
    public static void main(String[] args) {
        argumentParser = new JCommander(commandLineArgs);
        argumentParser.setProgramName("Dispatcher");

        try {
            argumentParser.parse(args);
        } catch (ParameterException ex) {
            System.err.println(ex.getMessage());
            usage(IrpUtils.EXIT_USAGE_ERROR);
        }

        if (commandLineArgs.helpRequested)
            usage(IrpUtils.EXIT_SUCCESS);

        if (commandLineArgs.versionRequested) {
            System.out.println(Version.versionString);
            System.out.println("JVM: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " " + System.getProperty("os.name") + "-" + System.getProperty("os.arch"));
            System.out.println();
            System.out.println(Version.licenseString);
            System.exit(IrpUtils.EXIT_SUCCESS);
        }

        Level logLevel = Level.INFO;
        if (commandLineArgs.loglevel != null) {
            try {
                logLevel = Level.parse(commandLineArgs.loglevel.toUpperCase(Locale.US));
            } catch (IllegalArgumentException e) {
                System.err.println("Illegal loglevel: " + commandLineArgs.loglevel + ", INFO will be used.");
            }
        }

        @SuppressWarnings("UnusedAssignment")
        Handler handler = null;
        if (commandLineArgs.logfile != null) {
            String filename = commandLineArgs.logfile;
            boolean append = filename.startsWith("+");
            if (append)
                filename = filename.substring(1);
            try {
                handler = new FileHandler(filename, append);
            } catch (IOException | SecurityException ex) {
                System.err.println(ex.getMessage());
                System.exit(IrpUtils.EXIT_IO_ERROR);
            }
        } else {
            handler = new ConsoleHandler();
        }
        handler.setLevel(logLevel);
        handler.setFormatter(new MyFormatter());
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(logLevel);

        String appHome = commandLineArgs.applicationHome != null ? commandLineArgs.applicationHome : System.getProperty("user.dir");
        logger.log(Level.FINE, "appHome = {0}", appHome);
        File libDir = Utils.libraryDir(appHome);
        logger.log(Level.FINE, "libDir = {0}", libDir);

        ICommandLineDevice hardware = null;
        if (commandLineArgs.device != null) {
            try {
                LocalSerialPort.setLibraryDir(libDir);
                hardware = new LocalSerialPortBuffered(commandLineArgs.device, commandLineArgs.verbose, null, commandLineArgs.baud);
            } catch (IOException ex) {
                System.err.println(ex.getMessage());
                doExit(IrpUtils.EXIT_IO_ERROR);
            }
        } else if (commandLineArgs.ip != null) {
            try {
                // FIXME untested
                hardware = new TcpSocketPort(commandLineArgs.ip, commandLineArgs.port, commandLineArgs.timeout, commandLineArgs.verbose, TcpSocketPort.ConnectionMode.keepAlive);
            } catch (UnknownHostException ex) {
                System.err.println(ex.getMessage());
                doExit(IrpUtils.EXIT_IO_ERROR);
            }
        } else {
            System.err.println("Either device or ip must be given.");
            doExit(IrpUtils.EXIT_SEMANTIC_USAGE_ERROR);
        }

        System.err.println("Starting " + Version.versionString);
        logger.info("Starting " + Version.versionString);
        int waitTime = commandLineArgs.waitTime;
        int maxTries = commandLineArgs.maxTries;
        Dispatcher dispatcher = null;
        try {
            dispatcher = new Dispatcher(new File(commandLineArgs.configFilename), hardware, commandLineArgs.girs, commandLineArgs.timeout);
            dispatcher.setVerbosity(commandLineArgs.verbose);
        } catch (IOException ex) {
            System.err.println("Could not initialize: " + ex.getMessage());
            logger.log(Level.SEVERE, "Error in constructor", ex);
            doExit(IrpUtils.EXIT_IO_ERROR);
        } catch (SAXException ex) {
            System.err.println("Could not read configuration file: " + ex.getMessage());
            logger.log(Level.SEVERE, "Configuration error file {0}", ex.getMessage());
            doExit(IrpUtils.EXIT_XML_ERROR);
        }

        boolean restart = false;
        int tries = 0;
        boolean virgin = true;
        do {
            try {
                restart = dispatcher.listen();
                tries = 0;
                virgin = false;
            } catch (HarcHardwareException | IOException ex) {
                // There are some severe problems (likely gnu.io.NoSuchPortException) if I got here.
                // If it is the first time, or too many retries, quit,...
                tries++;
                if (tries >= maxTries || virgin) {
                    System.err.println(ex.getMessage());
                    logger.severe(ex.getMessage());
                    if (virgin)
                        System.err.println("Cannot open the hardware, exiting.");
                    else
                        logger.log(Level.SEVERE, "Too many retries ({0}), giving up.", maxTries);
                    doExit(IrpUtils.EXIT_IO_ERROR);
                }
                // ... otherwise we wait a while and try again.
                logger.log(Level.SEVERE, "Problem in listener: {0}", ex.getMessage());
                logger.log(Level.INFO, "Waiting {0} seconds, then trying {1} more time(s).", new Object[]{waitTime, maxTries - tries});
                try {
                    Thread.sleep(1000L * waitTime);
                } catch (InterruptedException ex1) {
                }
            }
        } while (restart);
        logger.info("Normal shutdown");
    }
}

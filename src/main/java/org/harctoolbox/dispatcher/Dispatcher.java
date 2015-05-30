/*
Copyright (C) 2014 Bengt Martensson.

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
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.harctoolbox.IrpMaster.IrpUtils;
import org.harctoolbox.IrpMaster.XmlUtils;
import org.harctoolbox.harchardware.HarcHardwareException;
import org.harctoolbox.harchardware.IStringCommand;
import org.harctoolbox.harchardware.comm.LocalSerialPortBuffered;
import org.harctoolbox.harchardware.comm.TcpSocketPort;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
//#ifdef HAS_SIGNALS
import sun.misc.Signal;
import sun.misc.SignalHandler;
//#endif

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

    public final static int defaultPort = 22222;
    //private int debug = 0;
    private boolean verbose = false;
    private boolean stopRequested = false;
    private boolean restartRequested = false;
    private IStringCommand hardware;

    private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());

    private HashMap<IrCommand, ArrayList<Action>> map = new HashMap<>();
    private HashMap<String, Element> actionRefs = new HashMap<>();

    /**
     * @param verbose
     */
    public void setVerbosity(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     */
    public void requestStop() {
        stopRequested = true;
    }

    public void requestRestart() {
        stopRequested = true;
        restartRequested = true;
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
            ArrayList<Action> actions = new ArrayList<>();
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

    public Dispatcher(Document doc, IStringCommand hardware) throws IOException {
        parseDoc(doc);
        this.hardware = hardware;
    }

    public Dispatcher(File configFile, File schemaFile, IStringCommand hardware) throws IOException, SAXException {
        this(XmlUtils.openXmlFile(configFile, schemaFile, true, true), hardware);
    }

    public boolean listen(boolean increment) throws HarcHardwareException, IOException {
        restartRequested = false;
        stopRequested = false;
        if (LocalSerialPortBuffered.class.isInstance(hardware)) {
            LocalSerialPortBuffered lspb = (LocalSerialPortBuffered) hardware;
            lspb.open(increment);
            logger.log(Level.INFO, "Opened {0}", lspb.getActualPortName());
        } else
            hardware.open();

        logger.info("Listen started");
        int noTransmissions = 0;
        IrCommand old = null;
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
                String line = hardware.readString(); // may block
                if (/*debug > 0 &&*/ line != null && !line.isEmpty())
                    //System.out.println(line);
                    logger.finest(line);

                if (line == null || line.isEmpty() || line.equals("undecoded"))
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
                            logger.log(Level.WARNING, "Unparsable entry: {0}", line);
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
        ArrayList<Action> restrictions = map.get(irCommand);
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

    private void executeActions(ArrayList<AbstractAction> actions, String triggerName) {
        for (AbstractAction action : actions) {
            if (verbose)
                System.out.print(" " + action);
            logger.log(Level.INFO, "{0} -> {1}", new Object[]{triggerName, action});
            try {
                boolean success = action.action();
                if (!success)
                    //System.err.println(action + " failed.");
                    logger.log(Level.WARNING, "{0} failed", action.toString());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "IOException", ex);
            }
        }
    }

    private static void usage(int exitcode) {
        StringBuilder str = new StringBuilder();
        argumentParser.usage(str);

        (exitcode == IrpUtils.exitSuccess ? System.out : System.err).println(str);
        doExit(exitcode);
    }

    private static void doExit(int exitcode) {
        System.exit(exitcode);
    }

    private final static class CommandLineArgs {

        @Parameter(names = {"-b", "--baud"}, description = "Baudrate of serial port")
        private int baud = 115200;

        @Parameter(names = {"-c", "--config"}, description = "Path to the configuration file")
        private String configFilename = "listener.xml";

        @Parameter(names = {"-d", "--device"}, description = "Device name, e.g. COM7: or /dev/ttyUSB0")
        private String device = null;

        @Parameter(names = {"-h", "--help", "-?"}, description = "Display help message")
        private boolean helpRequested = false;

        @Parameter(names = {"-i", "--ip"}, description = "IP address or name")
        private String ip = null;

        @Parameter(names = {"-I", "--increment"}, description = "Increment the device if necessary")
        private boolean increment = false;

        @Parameter(names = {"-l", "--logfile"}, description = "Logfile")
        private String logfile = null;

        @Parameter(names = {"-L", "--loglevel"}, description = "Loglevel (ALL, FINE, INFO, SEVERE, WARNING, OFF,...)")
        private String loglevel = "INFO";

        @Parameter(names = {"-p", "--port"}, description = "Port number")
        private int port = defaultPort;

        @Parameter(names = {"-s", "--schema"}, description = "Schema file")
        private String schemaFileName = "schemas/event-action-map.xsd";

        @Parameter(names = {"-V", "--version"}, description = "Display version information")
        private boolean versionRequested;

        @Parameter(names = {"-v", "--verbose"}, description = "Execute commands verbosely")
        private boolean verbose;
    }

    private static JCommander argumentParser;
    private static CommandLineArgs commandLineArgs = new CommandLineArgs();

    public static void main(String[] args) {
        argumentParser = new JCommander(commandLineArgs);
        argumentParser.setProgramName("Dispatcher");

        try {
            argumentParser.parse(args);
        } catch (ParameterException ex) {
            System.err.println(ex.getMessage());
            usage(IrpUtils.exitUsageError);
        }

        if (commandLineArgs.helpRequested)
            usage(IrpUtils.exitSuccess);

        if (commandLineArgs.versionRequested) {
            System.out.println(Version.versionString);
            System.out.println("JVM: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " " + System.getProperty("os.name") + "-" + System.getProperty("os.arch"));
            System.out.println();
            System.out.println(Version.licenseString);
            System.exit(IrpUtils.exitSuccess);
        }

        Level logLevel = Level.INFO;
        if (commandLineArgs.loglevel != null) {
            try {
                logLevel = Level.parse(commandLineArgs.loglevel.toUpperCase(Locale.US));
            } catch (IllegalArgumentException e) {
                System.err.println("Illegal loglevel: " + commandLineArgs.loglevel + ", INFO will be used.");
            }
        }

        Handler handler = null;
        if (commandLineArgs.logfile != null) {
            try {
                handler = new FileHandler(commandLineArgs.logfile);
            } catch (IOException | SecurityException ex) {
                System.err.println(ex.getMessage());
                System.exit(4);
            }
        } else {
            handler = new ConsoleHandler();
        }
        handler.setLevel(logLevel);
        handler.setFormatter(new MyFormatter());
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(logLevel);

        IStringCommand hardware = null;
        if (commandLineArgs.device != null)
            try {
                hardware = new LocalSerialPortBuffered(commandLineArgs.device, commandLineArgs.baud, commandLineArgs.verbose);
            } catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException | IOException ex) {
                System.err.println(ex.getMessage());
                doExit(3);
            }
        else if (commandLineArgs.ip != null) {
            try {
                // FIXME untested
                hardware = new TcpSocketPort(commandLineArgs.ip, commandLineArgs.port, commandLineArgs.verbose, TcpSocketPort.ConnectionMode.keepAlive);
            } catch (UnknownHostException ex) {
                System.err.println(ex.getMessage());
                doExit(3);
            }
        } else {
            System.err.println("Either device or ip must be given.");
            doExit(1);
        }

        try {
            final Dispatcher dispatcher = new Dispatcher(new File(commandLineArgs.configFilename),
                    new File(commandLineArgs.schemaFileName),
                    hardware);
//#ifdef HAS_SIGNALS
            Signal.handle(new Signal("INT"), new SignalHandler() {
                @Override
                public void handle(Signal signal) {
                    System.err.println("Got SIGINT, quitting...");
                    dispatcher.requestStop();
                }
            });
            Signal.handle(new Signal("HUP"), new SignalHandler() {
                @Override
                public void handle(Signal signal) {
                    System.err.println("Got SIGHUP, restarting...");
                    dispatcher.requestRestart();
                }
            });
//#endif
            dispatcher.setVerbosity(commandLineArgs.verbose);
            boolean restart;
            do {
                restart = dispatcher.listen(commandLineArgs.increment);
            } while (restart);
        } catch (SAXException | HarcHardwareException | IOException ex) {
            System.err.println(ex.getMessage());
            doExit(2);
        }
    }
}

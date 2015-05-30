/* This file was automatically generated, do not edit. Do not check in in version management. */

package org.harctoolbox.dispatcher;

/**
 * This class contains version and license information and constants.
 */
public class Version {
    /** Verbal description of the license of the current work. */
    public final static String licenseString = "Copyright (C) 2014 Bengt Martensson.\n\nThis program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, see http://www.gnu.org/licenses/.";

    /** Verbal description of licenses of third-party components. */
    public final static String thirdPartyString = "Dispatcher uses the following uses the following software components: Serial communication is handeled by the RXTX library, licensed under the LGPL v 2.1 license. Command line decoding is done using the JCommander library by Cédric Beust http://jcommander.org/, licensed under the Apache 2.0 license. The wake on lan functionallity is implemented using the wakonlan library by Steffen Moldaner http://www.moldaner.de/wakeonlan/, licensed under the LGPL v 2.1 license.";

    public final static String appName = "Dispatcher";
    public final static int mainVersion = 0;
    public final static int subVersion = 0;
    public final static int subminorVersion = 1;
    public final static String versionSuffix = "";
    public final static String version = mainVersion + "." + subVersion + "." + subminorVersion + versionSuffix;
    public final static String versionString = appName + " version " + version;

    /** Project home page. */
    public final static String homepageUrl = "http://www.harctoolbox.org/";

    /** URL containing current official version. */
    public final static String currentVersionUrl = homepageUrl + "/downloads/" + appName + ".version";

    private Version() {
    }

    public static void main(String[] args) {
        System.out.println(versionString);
    }
}
    
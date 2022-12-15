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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.harctoolbox.harchardware.comm.Wol;
import org.harctoolbox.harchardware.misc.Ethers;

class WolAction extends AbstractAction {
    private Wol wol = null;
    private String history;

    WolAction(String ethernetAddressString, String hostname) {
        history = hostname + "/" + ethernetAddressString;
        String str = ethernetAddressString.isEmpty() ? hostname : ethernetAddressString;
        try {
            wol = new Wol(str);
        } catch (IOException | Ethers.MacAddressNotFound ex) {
            Logger.getLogger(Dispatcher.class.getName()).log(Level.SEVERE, "Can not resolve \"{0}\" to MAC address", str);
        }
    }

    @Override
    boolean action() throws IOException {
        if (wol != null) {
            wol.wol();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return wol != null ? wol.toString() : "Invalid WolAction: " + history;
    }
}

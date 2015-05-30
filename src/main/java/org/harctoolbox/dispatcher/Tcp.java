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
import org.harctoolbox.harchardware.comm.TcpSocketPort;

class Tcp extends AbstractAction {

    String host;
    int portNo;
    int timeOut;
    String payload;

    Tcp(String host, int portNo, String timeOut, String payload) {
        this.host = host;
        this.portNo = portNo;
        this.payload = payload;
        this.timeOut = timeOut != null && !timeOut.isEmpty() ? Integer.parseInt(timeOut) : TcpSocketPort.defaultTimeout;
    }

    @Override
    boolean action() throws IOException {
        try (TcpSocketPort tcpSocketPort = new TcpSocketPort(host, portNo, timeOut, false, TcpSocketPort.ConnectionMode.justInTime)) {
            tcpSocketPort.open();
            if (!tcpSocketPort.isValid())
                return false;
            tcpSocketPort.sendString(payload);
        }
        return true;
    }

    @Override
    public String toString() {
        return "tcp://" + host + ":" + portNo + " \"" + payload + "\"";
    }
}

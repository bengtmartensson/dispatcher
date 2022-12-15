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
import org.harctoolbox.harchardware.comm.UdpSocketChannel;

/**
 * This class does something interesting and useful. Or not...
 */
class Udp extends AbstractAction {

    String host;
    int portNo;
    int timeOut;
    String payload;

    Udp(String host, int portNo, String timeOut, String payload) {
        this.host = host;
        this.portNo = portNo;
        this.payload = payload;
        this.timeOut = timeOut != null && !timeOut.isEmpty() ? Integer.parseInt(timeOut) : TcpSocketPort.defaultTimeout;
    }

    @Override
    boolean action() throws IOException {
        UdpSocketChannel udpSocketChannel = new UdpSocketChannel(host, portNo, timeOut, false);
        //udpSocketChannel.connect();
        if (!udpSocketChannel.isValid())
            return false;
        //udpSocketChannel.open();
        udpSocketChannel.sendString(payload);
        udpSocketChannel.close();
        return true;
    }

    @Override
    public String toString() {
        return "udp://" + host + ":" + portNo + " \"" + payload + "\"";
    }
}

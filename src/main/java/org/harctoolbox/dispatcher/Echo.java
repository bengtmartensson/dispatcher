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

import java.util.logging.Logger;

class Echo extends AbstractAction {
    private final String payload;

    Echo(String payload) {
        this.payload = payload;
    }

    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    boolean action() {
        System.out.println(payload);
        Logger.getLogger(Dispatcher.class.getName()).info("echoing \"payload\"");
        return true;
    }

    @Override
    public String toString() {
        return "echo";
    }
}

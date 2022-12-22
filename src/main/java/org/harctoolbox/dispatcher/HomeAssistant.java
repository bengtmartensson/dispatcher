/*
Copyright (C) 2022 Bengt Martensson.

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
import org.harctoolbox.homeassistant.HomeAssistantAssistant;

public class HomeAssistant extends AbstractAction {

    private static final Logger logger = Logger.getLogger(HomeAssistant.class.getName());

    private final HomeAssistantAssistant haa;
    private final boolean verbose = true;
    private final String type;
    private final String kind;
    private final String domain;
    private final String service;
    private final String entity_id;

    public HomeAssistant(String host, int port, String token,
            String type, String kind, String domain, String service, String entity_id) throws IOException {
        haa = new HomeAssistantAssistant(host, port, token, verbose);
        this.type = type;
        this.kind = kind;
        this.domain = domain;
        this.service = service;
        this.entity_id = entity_id;
    }

    @Override
    boolean action() throws IOException {
        switch (kind) {
            case "services":
                return doServices();
            case "events":
                return doEvents();
            default:
                logger.log(Level.WARNING, "Unupported kind: {0}", kind);
                return false;
        }
    }

    private boolean doServices() throws IOException {
        switch (domain) {
            case "shell_command":
                return haa.shellCommand(service);
            case "homeassistant":
                return haa.services("homeassistant", service, entity_id);
            default:
                logger.log(Level.WARNING, "Unsupported domain: {0}", domain);
                return false;
        }
    }

    private boolean doEvents() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

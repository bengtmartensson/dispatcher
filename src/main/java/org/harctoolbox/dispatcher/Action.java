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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class Action {

    private static final Logger logger = Logger.getLogger(Action.class.getName());

    private int min = 1;
    private int max = 9999;
    private ArrayList<AbstractAction> actions;

    Action(Element element, String[] params, Dispatcher owner) {
        String minAtt = substitute(element.getAttribute("min"), params);
        min = minAtt.isEmpty() ? 1 : Integer.parseInt(minAtt);
        String maxAtt = substitute(element.getAttribute("max"), params);
        max = maxAtt.isEmpty() ? 9999 : Integer.parseInt(maxAtt);
        NodeList nl = element.getChildNodes();
        actions = new ArrayList<>(64);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element el = (Element) nl.item(i);
            String type = el.getTagName();
            AbstractAction action = null;
            switch (type) {
                case "echo":
                    action = new Echo(substitute(el.getTextContent(), params));
                    break;
                case "wol":
                    action = new WolAction(substitute(el.getAttribute("mac"), params),
                            substitute(el.getAttribute("hostname"), params));
                    break;
                case "quit":
                    action = new Quit(owner);
                    break;
                case "restart":
                    action = new Restart(owner);
                    break;
                case "tcp":
                    action = new Tcp(substitute(el.getAttribute("hostname"), params),
                            Integer.parseInt(substitute(el.getAttribute("port"), params)),
                            substitute(el.getAttribute("timeout"), params),
                            substitute(el.getTextContent(), params));
                    break;
                case "udp":
                    action = new Udp(substitute(el.getAttribute("hostname"), params),
                            Integer.parseInt(substitute(el.getAttribute("port"), params)),
                            substitute(el.getAttribute("timeout"), params),
                            substitute(el.getTextContent(), params));
                    break;
                case "http":
                    action = new Http(substitute(el.getAttribute("url"), params));
                    break;
                case "exec": {
                    NodeList argumentNodes = el.getElementsByTagName("argument");
                    String[] arguments = new String[argumentNodes.getLength()];
                    for (int j = 0; j < argumentNodes.getLength(); j++)
                        arguments[j] = substitute(argumentNodes.item(j).getTextContent(), params);

                    action = new Exec(substitute(el.getAttribute("progname"), params),
                            arguments,
                            substitute(el.getAttribute("wait"), params).equals("true"),
                            substitute(el.getAttribute("directory"), params),
                            substitute(el.getAttribute("in"), params),
                            substitute(el.getAttribute("out"), params),
                            substitute(el.getAttribute("err"), params));
                }
                break;
                case "homeassistant": {
                    try {
                        action = new HomeAssistant(
                                substitute(el.getAttribute("host"), params),
                                Integer.parseInt(substitute(el.getAttribute("port"), params)),
                                substitute(el.getAttribute("token"), params),
                                substitute(el.getAttribute("type"), params),
                                substitute(el.getAttribute("kind"), params),
                                substitute(el.getAttribute("domain"), params),
                                substitute(el.getAttribute("service"), params),
                                substitute(el.getAttribute("entity_id"), params)
                        );
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getLocalizedMessage());
                        action = null;
                    }
                }
                break;

                default:
                    Logger.getLogger(Dispatcher.class.getName()).log(Level.INFO, "Unknown action {0}", type);
            }
            actions.add(action);
        }
    }

    /**
     * @return the min
     */
    public int getMin() {
        return min;
    }

    /**
     * @return the max
     */
    public int getMax() {
        return max;
    }

    /**
     * @return the actions
     */
    public List<AbstractAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    private String substitute(String in, String[] parameters) {
        String str = in;
        for (int i = 0; i < parameters.length; i++) {
            str = str.replaceAll("\\$" + Integer.toString(i+1), parameters[i]);
        }
        return str;
    }
}

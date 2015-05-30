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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

class Http extends AbstractAction {
    URL url = null;

    Http(String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    @Override
    boolean action() throws IOException {
          //if (verbose)
        //  System.err.println("Getting URL " + url);
        if (url == null)
            return false;
        InputStream junk = url.openStream();
        junk.close();
        return true;
    }

    @Override
    public String toString() {
        return url.toString();
    }

}

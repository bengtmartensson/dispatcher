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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

class Exec extends AbstractAction {

    private final ArrayList<String> cmdArray = new ArrayList<>();
    private final boolean waitProc;
    private final ProcessBuilder processBuilder;
    private final String shellForm;

    Exec(String progpath, String[] arguments, boolean wait, String directory, String in, String out, String err) {
        cmdArray.add(progpath);
        cmdArray.addAll(Arrays.asList(arguments));
        processBuilder = new ProcessBuilder(cmdArray);
        processBuilder.directory(directory.isEmpty() ? null : new File(directory));
        if (in != null && !in.isEmpty())
            processBuilder.redirectInput(new File(in));
        processBuilder.redirectOutput((out != null && !out.isEmpty())
                    ? ProcessBuilder.Redirect.to(new File(out))
                    : ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError((err != null && !err.isEmpty())
                ? ProcessBuilder.Redirect.to(new File(err))
                : ProcessBuilder.Redirect.INHERIT);
        this.waitProc = wait;

        StringBuilder str = new StringBuilder();
        if (!directory.isEmpty())
            str.append("( cd ").append(directory).append(" ; ");
        for (String s : cmdArray)
            str.append(s).append(" ");
        if (in != null && !in.isEmpty())
            str.append("< ").append(in);
        if (out != null && !out.isEmpty())
            str.append("> ").append(out);
        if (err != null && !err.isEmpty())
            str.append("2> ").append(err);
        if (!directory.isEmpty())
            str.append(")");
        if (!wait)
            str.append("& ");

        shellForm = str.toString();
    }

    @Override
    boolean action() throws IOException {
        Process process = processBuilder.start();
        int errorCode = 0;
        if (waitProc) {
            try {
                errorCode = process.waitFor();
            } catch (InterruptedException ex) {
                Logger.getLogger(Dispatcher.class.getName()).warning(ex.getMessage());
            }
        }
        return errorCode == 0;
    }

    @Override
    public String toString() {
        return "exec " +  shellForm;
    }
}

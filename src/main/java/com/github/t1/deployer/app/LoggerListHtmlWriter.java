package com.github.t1.deployer.app;

import javax.ws.rs.ext.Provider;

import com.github.t1.deployer.model.LoggerConfig;

@Provider
public class LoggerListHtmlWriter extends AbstractListHtmlWriter<LoggerConfig> {
    public LoggerListHtmlWriter() {
        super(LoggerConfig.class);
    }

    @Override
    protected String title() {
        return "Loggers";
    }

    @Override
    protected void body() {
        link("&lt;", Deployments.pathAll(uriInfo));
        br();
        out.append("    <table>\n");
        for (LoggerConfig logger : target) {
            out.append("        <tr><td>");
            link(logger.getCategory(), Loggers.path(uriInfo, logger));
            out.append("</td><td>");
            out.append(logger.getLevel());
            out.append("</td><td>");
            out.append(delete(logger));
            out.append("</td></tr>\n");
        }
        out.append("    <tr><td colspan='3'><a href=\"" + Loggers.newLogger(uriInfo) + "\">+</a></td></tr>");
        out.append("    </table>\n");
    }

    private String delete(LoggerConfig logger) {
        return "<form method=\"POST\" action=\"" + Loggers.path(uriInfo, logger) + "\">\n" //
                + "  <input type=\"hidden\" name=\"action\" value=\"delete\">\n" //
                + "  <input type=\"submit\" value=\"Delete\">\n" //
                + "</form>";
    }
}

package com.github.t1.deployer.app.html;

import static com.github.t1.deployer.app.html.Navigation.*;
import static java.util.Collections.*;

import javax.ws.rs.ext.Provider;

import com.github.t1.deployer.app.Deployments;
import com.github.t1.deployer.model.Deployment;
import com.github.t1.deployer.tools.User;

@Provider
public class DeploymentListHtmlWriter extends AbstractListHtmlBodyWriter<Deployment> {
    public DeploymentListHtmlWriter() {
        super(Deployment.class, DEPLOYMENTS);
    }

    User user = User.getCurrent();

    @Override
    public void body() {
        append("    <table>\n");
        sort(getTarget());
        for (Deployment deployment : getTarget()) {
            append("        <tr>");

            append("<td>") //
                    .append(href(deployment.getContextRoot().getValue(),
                            Deployments.path(getUriInfo(), deployment.getContextRoot()))) //
                    .append("</td>");

            append("<td>").append(deployment.getName()).append("</td>");

            append("<td title=\"SHA-1: ").append(deployment.getCheckSum()).append("\">") //
                    .append(deployment.getVersion()) //
                    .append("</td>");

            append("</tr>\n");
        }
        append("    <tr><td colspan='3'>") //
                .append(href("+", Deployments.newDeployment(getUriInfo()))) //
                .append("</td></tr>\n");
        append("    </table>\n");
        append("<br/><br/>\n");
    }
}
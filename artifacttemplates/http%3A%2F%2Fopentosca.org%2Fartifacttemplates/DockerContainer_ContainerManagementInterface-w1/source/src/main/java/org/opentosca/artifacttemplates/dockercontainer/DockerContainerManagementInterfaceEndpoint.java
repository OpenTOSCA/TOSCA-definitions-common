package org.opentosca.artifacttemplates.dockercontainer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.opentosca.artifacttemplates.OpenToscaHeaders;
import org.opentosca.artifacttemplates.SoapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;

import static org.opentosca.artifacttemplates.dockercontainer.FileHandler.downloadFile;
import static org.opentosca.artifacttemplates.dockercontainer.FileHandler.getFile;
import static org.opentosca.artifacttemplates.dockercontainer.FileHandler.getUrl;

@Endpoint
public class DockerContainerManagementInterfaceEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerManagementInterfaceEndpoint.class);

    @PayloadRoot(namespace = DockerContainerConstants.NAMESPACE_URI, localPart = "runScriptRequest")
    public void runScript(@RequestPayload RunScriptRequest request, MessageContext messageContext) {
        LOG.info("RunScript request received");

        OpenToscaHeaders openToscaHeaders = SoapUtil.parseHeaders(messageContext);
        InvokeResponse invokeResponse = new InvokeResponse();
        invokeResponse.setMessageID(openToscaHeaders.messageId());

        try {
            DockerContainer container = new DockerContainer(request.getDockerEngineURL(), request.getDockerEngineCertificate(), request.getContainerID());
            container.awaitAvailability();
            container.ensurePackage("sudo");
            String command = container.replaceHome(request.getScript(), true);
            String result = container.execCommand(command);
            invokeResponse.setScriptResult(result);
            LOG.info("RunScript request successful");
        } catch (InterruptedException e) {
            LOG.error("Could not execute script", e);
            invokeResponse.setError("Could not execute script: " + e.getMessage());
        }

        SoapUtil.sendSoapResponse(invokeResponse, InvokeResponse.class, openToscaHeaders.replyTo());
    }

    @PayloadRoot(namespace = DockerContainerConstants.NAMESPACE_URI, localPart = "transferFileRequest")
    public void transferFile(@RequestPayload TransferFileRequest request, MessageContext messageContext) {
        LOG.info("TransferFile request received");

        OpenToscaHeaders openToscaHeaders = SoapUtil.parseHeaders(messageContext);
        InvokeResponse invokeResponse = new InvokeResponse();
        invokeResponse.setMessageID(openToscaHeaders.messageId());

        try {
            DockerContainer container = new DockerContainer(request.getDockerEngineURL(), request.getDockerEngineCertificate(), request.getContainerID());
            container.awaitAvailability();

            // TODO: refactor this
            String target = request.getTargetAbsolutePath();
            if (target.startsWith("~")) {
                target = container.replaceHome(target, false);
            }

            // CASE: Transfer file from URL to container
            URL url = getUrl(request.getSourceURLorLocalPath());
            if (url != null) {
                LOG.info("Transferring file from URL '{}' to container", request.getSourceURLorLocalPath());
                String filename = target.substring(target.lastIndexOf('/') + 1);
                Path directory = Files.createTempDirectory(filename);
                String source = downloadFile(url, directory.toString(), filename);
                container.uploadFile(source, target);
                container.convertToUnix(target);
                FileUtils.deleteDirectory(directory.toFile());
                LOG.info("Deleted temporary directory successful");
                invokeResponse.setTransferResult("successful");
                LOG.info("TransferFile request successful");
                SoapUtil.sendSoapResponse(invokeResponse, InvokeResponse.class, openToscaHeaders.replyTo());
                return;
            }

            // CASE: Transfer local file to container
            File file = getFile(request.getSourceURLorLocalPath());
            if (file != null) {
                LOG.info("Transferring local file '{}' to container", request.getSourceURLorLocalPath());
                String source = file.toString();
                container.uploadFile(source, target);
                container.convertToUnix(target);
                invokeResponse.setTransferResult("successful");
                LOG.info("TransferFile request successful");
                SoapUtil.sendSoapResponse(invokeResponse, InvokeResponse.class, openToscaHeaders.replyTo());
                return;
            }

            // DEFAULT
            String message = "File " + request.getSourceURLorLocalPath() + " is no valid URL and does not exist on the local file system.";
            LOG.error(message);
            invokeResponse.setError("Could not transfer file: " + message);
            SoapUtil.sendSoapResponse(invokeResponse, InvokeResponse.class, openToscaHeaders.replyTo());

        } catch (InterruptedException | IOException e) {
            LOG.error("Could not transfer file", e);
            invokeResponse.setError("Could not transfer file: " + e.getMessage());
            SoapUtil.sendSoapResponse(invokeResponse, InvokeResponse.class, openToscaHeaders.replyTo());
        }
    }
}

package org.opentosca.artifacttemplates.ubuntuvm;

import java.io.File;
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

import static org.opentosca.artifacttemplates.ubuntuvm.FileHandler.downloadFile;
import static org.opentosca.artifacttemplates.ubuntuvm.FileHandler.getFile;
import static org.opentosca.artifacttemplates.ubuntuvm.FileHandler.getUrl;

@Endpoint
public class UbuntuVMOperatingSystemInterfaceEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(UbuntuVMOperatingSystemInterfaceEndpoint.class);

    @PayloadRoot(namespace = UbuntuVMConstants.NAMESPACE_URI, localPart = "installPackageRequest")
    public void installPackages(@RequestPayload InstallPackageRequest request, MessageContext messageContext) {
        LOG.info("InstallPackages request received!");

        OpenToscaHeaders openToscaHeaders = SoapUtil.parseHeaders(messageContext);
        InvokeResponse response = new InvokeResponse();
        response.setMessageID(openToscaHeaders.messageId());

        boolean success = false;

        VirtualMachine VM = new VirtualMachine(request.getVMIP(), request.getVMUserName(), request.getVMPrivateKey());
        try {
            VM.connect();
            success = VM.installPackages(request.getPackageNames());
        } catch (Exception e) {
            LOG.error("Could not install packages", e);
            response.setError("Could not install packages: " + e.getMessage());
        } finally {
            VM.disconnect();
        }

        if (success) {
            LOG.info("InstallPackages request successful");
            response.setInstallResult("1");
        } else {
            response.setInstallResult("0");
        }
        SoapUtil.sendSoapResponse(response, InvokeResponse.class, openToscaHeaders.replyTo());
    }

    @PayloadRoot(namespace = UbuntuVMConstants.NAMESPACE_URI, localPart = "transferFileRequest")
    public void transferFile(@RequestPayload TransferFileRequest request, MessageContext messageContext) {
        LOG.info("TransferFile request received");

        OpenToscaHeaders openToscaHeaders = SoapUtil.parseHeaders(messageContext);
        InvokeResponse response = new InvokeResponse();
        response.setMessageID(openToscaHeaders.messageId());

        VirtualMachine VM = new VirtualMachine(request.getVMIP(), request.getVMUserName(), request.getVMPrivateKey());
        try {
            // Connect to VM
            VM.connect();

            // Source and target paths
            String source = null;
            String target = VM.replaceHome(request.getTargetAbsolutePath());

            // Use file from URL as source if valid URL
            Path tempDirectory = null;
            URL url = getUrl(request.getSourceURLorLocalPath());

            if (url != null) {
                LOG.info("Transferring file from URL '{}' to VM", request.getSourceURLorLocalPath());
                String filename = target.substring(target.lastIndexOf('/') + 1);
                tempDirectory = Files.createTempDirectory(filename);
                source = downloadFile(url, tempDirectory.toString(), filename);
            }

            // Use local file as source if file exists
            File file = getFile(request.getSourceURLorLocalPath());
            if (file != null) {
                LOG.info("Transferring local file '{}' to VM", request.getSourceURLorLocalPath());
                source = file.toString();
            }

            // Abort if no source
            if (source == null) {
                String message = "File " + request.getSourceURLorLocalPath() + " is no valid URL and does not exist on the local file system.";
                LOG.error(message);
                response.setError("Could not transfer file: " + message);
                SoapUtil.sendSoapResponse(response, InvokeResponse.class, openToscaHeaders.replyTo());
                return;
            }

            // Upload source to target on VM
            VM.uploadFile(source, target);

            // Convert target on VM to unix
            VM.convertToUnix(target);

            // Clean up
            if (tempDirectory != null) {
                FileUtils.deleteDirectory(tempDirectory.toFile());
                LOG.info("Deleted temporary directory successful");
            }

            // Success
            response.setTransferResult("successful");
            LOG.info("TransferFile request successful");
        } catch (Exception e) {
            LOG.error("Could not transfer file...", e);
            response.setError("Could not transfer file: " + e.getMessage());
        } finally {
            VM.disconnect();
        }

        // Send response
        SoapUtil.sendSoapResponse(response, InvokeResponse.class, openToscaHeaders.replyTo());
    }

    @PayloadRoot(namespace = UbuntuVMConstants.NAMESPACE_URI, localPart = "runScriptRequest")
    public void runScript(@RequestPayload RunScriptRequest request, MessageContext messageContext) {
        LOG.info("RunScript request received!");

        OpenToscaHeaders openToscaHeaders = SoapUtil.parseHeaders(messageContext);
        InvokeResponse response = new InvokeResponse();
        response.setMessageID(openToscaHeaders.messageId());

        VirtualMachine VM = new VirtualMachine(request.getVMIP(), request.getVMUserName(), request.getVMPrivateKey());
        try {
            VM.connect();
            VM.installPackages("sudo");
            String command = VM.replaceHome(request.getScript());
            String result = VM.execCommand(command);
            response.setScriptResult(SoapUtil.encode(result));
            LOG.info("RunScript request successful");
        } catch (Exception e) {
            LOG.error("Could not execute script", e);
            response.setError("Could not execute script: " + e.getMessage());
        } finally {
            VM.disconnect();
        }

        SoapUtil.sendSoapResponse(response, InvokeResponse.class, openToscaHeaders.replyTo());
    }

    @PayloadRoot(namespace = UbuntuVMConstants.NAMESPACE_URI, localPart = "waitForAvailabilityRequest")
    public void waitForAvailabilityRequest(@RequestPayload WaitForAvailabilityRequest request, MessageContext messageContext) {
        LOG.info("WaitForAvailability request received!");

        OpenToscaHeaders openToscaHeaders = SoapUtil.parseHeaders(messageContext);
        InvokeResponse response = new InvokeResponse();
        response.setMessageID(openToscaHeaders.messageId());

        VirtualMachine VM = new VirtualMachine(request.getVMIP(), request.getVMUserName(), request.getVMPrivateKey());
        try {
            VM.connect();
            LOG.info("WaitForAvailability request successful");
            response.setWaitResult("Success");
        } catch (Exception e) {
            LOG.error("Could not wait for availability", e);
            response.setError("Could not wait for availability: " + e.getMessage());
            response.setWaitResult("Error");
        } finally {
            VM.disconnect();
        }

        SoapUtil.sendSoapResponse(response, InvokeResponse.class, openToscaHeaders.replyTo());
    }

    @PayloadRoot(namespace = UbuntuVMConstants.NAMESPACE_URI, localPart = "testPortBindingRequest")
    public void testPortBinding(@RequestPayload TestPortBindingRequest request, MessageContext messageContext) {
        // TODO: testPortBinding
    }
}

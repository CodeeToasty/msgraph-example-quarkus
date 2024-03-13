package it.codeetoasty;

import io.quarkus.test.junit.QuarkusTest;
import it.codeetoasty.msgraphexamplequarkus.sharepoint.services.SharepointService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

@QuarkusTest
public class SharepointServiceTest {


    @Inject
    SharepointService sharepointService;

    @Test
    public void uploadSmallFile() throws Exception {
        sharepointService.normalUpload("folders/path","small.txt","test".getBytes());
    }

    @Test
    public void uploadLargeFile() throws URISyntaxException {
        //100MB file
        byte[] largeFile = new byte[1048576*10];
        Arrays.fill(largeFile, (byte) 1);
        sharepointService.normalUpload("folders/path","large.txt",largeFile);
    }

    @Test
    public void createFolderStructure(){
        String path = "folders/path/xxx/yyy";
        String[] pathArray = path.split("/");
        sharepointService.recursiveCreateFolder(pathArray);
    }

    @Test
    public void downloadFile() throws IOException {
        byte[] test = sharepointService.downloadItem("path/to/file");
    }




}

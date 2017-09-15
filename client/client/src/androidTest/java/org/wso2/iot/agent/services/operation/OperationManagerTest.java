package org.wso2.iot.agent.services.operation;

import android.content.Context;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;


import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.Operation;
import org.wso2.iot.agent.services.operation.util.MockOperationManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@RunWith(AndroidJUnit4.class)
public class OperationManagerTest {

    private FtpServer server;
    private String ftpDirectory = Environment.getExternalStorageDirectory().getPath();
    private String deviceDownloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .toString();

    @Before
    public void setupFTPServer() throws Exception {

        File file = new File(ftpDirectory + File.separator + "testFile.txt");

        if(file.createNewFile()) { //Create the file
            if (!file.exists()){
                Assert.fail("File creation in device failed.");
            }
        }

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        UserManager userManager = userManagerFactory.createUserManager();
        BaseUser user = new BaseUser();
        user.setName("test");
        user.setPassword("test");
        List<Authority> authorities = new ArrayList<>();
        authorities.add(new WritePermission());
        user.setAuthorities(authorities);
        user.setHomeDirectory(ftpDirectory);

        try {
            userManager.save(user);
        } catch (FtpException e) {
            e.printStackTrace();
        }

        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(2221);

        FtpServerFactory factory = new FtpServerFactory();
        factory.setUserManager(userManager);
        factory.addListener("default", listenerFactory.createListener());

        server = factory.createServer();

        try {
            server.start();
        } catch (FtpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDownloadFile() {

        Context context = InstrumentationRegistry.getTargetContext();
        OperationManager operationManager = new MockOperationManager(context);

        Object[] payloads = new Object[2];

        payloads[0] = "{\"fileURL\":\"ftp://test@localhost:2221/testFile.txt\"," +
                "\"ftpPassword\":\"test\",\"fileLocation\":\"" + deviceDownloadDirectory + "\"}";

        payloads[1] = "{\"fileURL\":\"ftp://test@localhost:2221/testFile.txt\"," +
                "\"ftpPassword\":\"test\",\"fileLocation\":\"\"}";

        for (Object payload : payloads) {
            org.wso2.iot.agent.beans.Operation operation = new Operation();
            operation.setPayLoad(payload);
            Log.d("", "Testing for payload: " + payload);
            try {
                operationManager.downloadFile(operation);
            } catch (AndroidAgentException e) {
                e.printStackTrace();
            }
            Log.d("", "Results: " + operation.getOperationResponse());
            Assert.assertEquals(operation.getOperationResponse(), "COMPLETED", operation.getStatus());
        }
    }

    @Test
    public void testUploadFile() {

        Context context = InstrumentationRegistry.getTargetContext();
        OperationManager operationManager = new MockOperationManager(context);

        Object[] payloads = new Object[1];

        payloads[0] = "{\"fileURL\":\"ftp://test@localhost:2221/\"," +
                "\"ftpPassword\":\"test\",\"fileLocation\":\"" + deviceDownloadDirectory +
                File.separator + "testFile.txt\"}";

        for (Object payload : payloads) {
            org.wso2.iot.agent.beans.Operation operation = new Operation();
            operation.setPayLoad(payload);
            Log.d("", "Testing for payload: " + payload);
            try {
                operationManager.uploadFile(operation);
            } catch (AndroidAgentException e) {
                e.printStackTrace();
            }
            Log.d("", "Results: " + operation.getOperationResponse());
            Assert.assertEquals(operation.getOperationResponse(), "COMPLETED", operation.getStatus());
        }
    }

    @After
    public void stopSFTPServer() {
        System.out.println("server stopped ? " + server.isStopped());
        if (server != null) {
            server.stop();
        }
        System.out.println("server stopped ? " + server.isStopped());
    }
}

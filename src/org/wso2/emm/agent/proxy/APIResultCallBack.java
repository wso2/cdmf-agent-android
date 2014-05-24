package org.wso2.emm.agent.proxy;

import java.util.Map;

public interface APIResultCallBack {

	public void onReceiveAPIResult(Map <String,String> result, int requestCode);
}

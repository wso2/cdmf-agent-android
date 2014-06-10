package org.wso2.emm.agent.proxy;


public interface TokenCallBack {
	public void onReceiveTokenResult(Token token,String status);
}

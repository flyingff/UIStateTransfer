package net.flyingff.commons.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.function.Consumer;

import net.flyingff.commons.ui.StateTransfer;

public class NetEndpoint {
	public static final String SNOTCONN = "NC", SWAITSERV = "WSERV", SWAITCLNT = "WCLNT",
			SLINKED = "LNKED", SCLOSED = "DOWN", SERR = "ERR";
	private String name;
	private int port;
	private AsynchronousSocketChannel ac;
	private StateTransfer t = new StateTransfer();
	private Exception lastError = null;
	private AsynchronousServerSocketChannel assc = null;
	private AsynchronousSocketChannel asc = null;
	
	public NetEndpoint setName(String name) {
		this.name = name;
		return this;
	}
	public String getName() {
		return name;
	}
	public String getState(){
		return t.state();
	}
	
	public NetEndpoint(int port){
		this(port, "Name not set");
	}
	public NetEndpoint(int port, String name) {
		this.port = port;
		this.name = name;
		t.eps(SNOTCONN, SWAITCLNT, SWAITSERV, SLINKED, SCLOSED, SERR);
		t.ep(SNOTCONN).edge("service", ()->{
			try {
				assc = AsynchronousServerSocketChannel.open();
				assc.bind(new InetSocketAddress(InetAddress.getLocalHost(), port));
			} catch(Exception e) {
				lastError = e;
				t.ex();
			}
		}, SWAITCLNT);
		t.exdef(SERR);
	}
	
	private Consumer<Throwable> errHandler = null;
	private Consumer<InetAddress> conHandler = null;
	public void setExceptionHandler(Consumer<Throwable> handler) {
		errHandler = handler;
	}
	public void setConnectHandler(Consumer<InetAddress> handler) {
		conHandler = handler;
	}
	
	private String connAddr;
	public void connect(String addr) {
		connAddr = addr;
		t.event("connect");
	}
	
	private void service(){
		t.event("service");
	}
}

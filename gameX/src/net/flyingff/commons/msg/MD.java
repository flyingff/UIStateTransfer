package net.flyingff.commons.msg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class MD {
	private Queue<Message> mq = new LinkedList<>();
	private static MD inst = new MD();
	public static void sendMessage(String name, Object... param) {
		synchronized (inst) {
			inst.mq.add(new Message(name, param));
			inst.notify();
		}
	}
	public static boolean regist(String name, MsgListener l){
		boolean reged = true;
		Set<MsgListener> s = inst.reg.get(name);
		if(s == null) {
			s = new HashSet<>();
			inst.reg.put(name, s);
			reged = false;
		}
		s.add(l);
		return reged;
	}
	private Map<String, Set<MsgListener>> reg = new HashMap<>();
	private MD() {
		new Thread(()->{
			Message m = null;
			while(true) {
				synchronized (this) {
					if(mq.isEmpty()) {
						try { wait(); } catch (Exception e) { }
					}
					m = mq.poll();
				}
				Set<MsgListener> s = reg.get(m.name);
				if(s != null) {
					for(final MsgListener mlx : s) {
						mlx.onMsg(m.params);
					}
				}
			}
		}).start();
	}
	
	private static class Message {
		public String name;
		public Object[] params;
		public Message(String n, Object[] p) {
			this.name = n; this.params = p;
		}
	}
	public static void main(String[] args) throws Exception{
		MD.regist("test", e->{
			System.out.println(e[0]);
			if("exit".equals(e[0]))
				System.exit(0);
		});
		MD.sendMessage("test", "Test message!");
		Thread.sleep(1000);
		MD.sendMessage("test", "Second Msg!");
		Thread.sleep(1000);
		MD.sendMessage("test", "exit");
	}
}

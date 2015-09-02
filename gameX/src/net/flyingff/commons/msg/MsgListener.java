package net.flyingff.commons.msg;

@FunctionalInterface
public interface MsgListener {
	void onMsg(Object[] param);
}

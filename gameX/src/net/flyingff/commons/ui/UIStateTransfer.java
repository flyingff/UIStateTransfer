package net.flyingff.commons.ui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class UIStateTransfer {
	private static final String D$EF$EX = new String(new byte[]{0x21,0x0,0x12,0x44,(byte) 0xff,(byte) 0xc4,0x42});
	private Map<String, UIState> states = new HashMap<>();
	private Map<String, UIState> exceptions = new HashMap<>();
	private Map<String, Set<Runnable>> events = new HashMap<>();
	private Set<VariantObject<?>> vars = new HashSet<>();
	private UIState current;
	// 工具变量
	private ScheduledExecutorService ses = Executors.newScheduledThreadPool(4);

	public UIState ep(String name, Runnable in, Runnable out){
		UIState sx = states.get(name);
		if(sx == null){
			sx = new UIState(in, out, name);
			states.put(name, sx);
		}
		return sx;
	}
	public UIState ep(String name){
		return ep(name, null, null);
	}
	public void start(String fistStateame){
		UIState sx = states.get(fistStateame);
		if(sx == null) {
			throw new RuntimeException("Unknown state name:" + fistStateame);
		}
		switchTo(sx);
	}
	public void eps(String... name) {
		for(final String sx : name) {
			if(states.containsKey(sx)) throw new RuntimeException("Duplicated state name: " + name);
			states.put(sx, new UIState(null, null, sx));
		}
	}
	public void exdef(String targetState) {
		exdef(D$EF$EX, targetState);
	}
	public void exdef(String exceptionName, String targetState) {
		if(states.get(targetState) == null)
			throw new RuntimeException("Undefined exception state: " + targetState);
		exceptions.put(exceptionName, states.get(targetState));
	}
	public void ex(){
		ex(D$EF$EX);
	}
	public void ex(String exceptionName) {
		UIState target = exceptions.get(exceptionName);
		if(target != null) {
			switchTo(target);
		} else {
			throw new RuntimeException("Unknow exception:" + exceptionName);
		}
	}
	public void event(String event){
		Set<Runnable> actset = events.get(event);
		if(event != null) for(Runnable rx : actset){
			rx.run();
		}
	}
	@SafeVarargs
	public final <T> VariantObject<T> with(Consumer<T>... m) {
		VariantObject<T> vx = new VariantObject<T>(m);
		vars.add(vx);
		return vx;
	}
	private boolean isCurrent(UIState s){ 
		return s == current;
	}
	private void switchTo(UIState next) {
		if(current != null)
			current.leave();
		current = next;
		for(@SuppressWarnings("rawtypes") final VariantObject vx : vars) {
			vx.onChange();
		}
		next.into();
	}
	private void regEvent(String eventName, Runnable r) {
		Set<Runnable> actset = events.get(eventName);
		if(actset == null) {
			actset = new HashSet<>();
			events.put(eventName, actset);
		}
		actset.add(r);
	}
	
	public final class UIState {
		private final List<Runnable> runs = new ArrayList<>();
		private Runnable into, leave;
		public final String name;
		private UIState(Runnable into, Runnable leave, String name) {
			this.into = into;
			this.leave = leave;
			this.name = name;
		}
		private final void into(){
			if(into != null) {into.run();}
			for(final Runnable rx : runs) rx.run();
		}
		private final void leave(){
			if(leave != null) leave.run();
		}
		public final UIState edge(ActionListenerAdder method, String next) {
			return edge(method, null, next);
		}
		public final UIState edge(ActionListenerAdder method, Consumer<ActionEvent> act, String next){
			final UIState nexts = states.get(next);
			if(nexts == null) throw new RuntimeException("State not found: " + next);
			method.addActionListener(e -> {
				if(isCurrent(this)) {if(act != null) act.accept(e);; switchTo(nexts); }
			});
			return this;
		}
		public final UIState edge(ActionListenerAdder method, Callable<String> act){
			if(act == null) throw new NullPointerException();
			method.addActionListener(e -> {
				if(isCurrent(this)) {
					try {
						String next;
						next = act.call();
						final UIState nexts = states.get(next);
						if(nexts == null) throw new RuntimeException("State not found: " + next);
						switchTo(nexts); 
					} catch (Exception e1) {
						e1.printStackTrace();
						throw new RuntimeException(e1);
					}
				}
			});
			return this;
		}
		public final UIState edge(String event, String next) {
			return edge(event, null, next);
		}
		public final UIState edge(String event, Runnable act, String next){
			final UIState nexts = states.get(next);
			if(nexts == null) throw new RuntimeException("State not found: " + next);
			regEvent(event, ()-> {
				if(isCurrent(this)) {if(act != null) act.run(); switchTo(nexts); }
			});
			return this;
		}
		public final UIState edge(String event, Callable<String> act){
			if(act == null) throw new NullPointerException();
			regEvent(event, () -> {
				if(isCurrent(this)) {
					try {
						String next;
						next = act.call();
						final UIState nexts = states.get(next);
						if(nexts == null) throw new RuntimeException("State not found: " + next);
						switchTo(nexts); 
					} catch (Exception e1) {
						e1.printStackTrace();
						throw new RuntimeException(e1);
					}
				}
			});
			return this;
		}
		
		public final UIState jmpLater(int sleepms, String next) {
			return jmpLater(sleepms, null, next);
		}
		public final UIState jmpLater(int sleepms, Runnable act, String next){
			final UIState nexts = states.get(next);
			if(nexts == null) throw new RuntimeException("State not found: " + next);
			runs.add(() -> {
				ses.schedule(() -> {
					if(isCurrent(this)) {if(act != null) act.run(); switchTo(nexts); }
				}, sleepms, TimeUnit.MILLISECONDS);
			});
			return this;
		}
		public final UIState into(Runnable r) {
			this.into = r;
			return this;
		}
		public final UIState leave(Runnable r) {
			this.leave = r;
			return this;
		}
		@SafeVarargs
		public final <T> UIState set(T val, Consumer<T>... m) {
			runs.add(() -> {for(Consumer<T> mx : m) mx.accept(val);});
			return this;
		}
	}
	public final class VariantObject<T>{
		private final HashMap<String, T> map = new HashMap<>();
		private final Consumer<T>[] consumers;
		private T def = null;
		@SafeVarargs
		private VariantObject(Consumer<T> ... c){ consumers = c; }
		public VariantObject<T> when(T val,String... states) {
			for(final String sx : states) {
				if(map.containsKey(sx))
					throw new RuntimeException("Duplicated case condition: " + sx);
				map.put(sx, val);
			}
			return this;
		}
		public void otherthan(T val) {
			def = val;
		}
		private void onChange(){
			T vx;
			if(map.containsKey(current.name)) {
				vx = map.get(current.name);
			} else {
				vx = def;
			}
			for(Consumer<T> cx : consumers) {
				cx.accept(vx);
			}
		}
	}
}

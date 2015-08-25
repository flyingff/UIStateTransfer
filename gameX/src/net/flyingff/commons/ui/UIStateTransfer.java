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
	// Tool variable
	private ScheduledExecutorService ses = Executors.newScheduledThreadPool(4);

	/**
	 * Get the reference of a state. If the specified state does not exit,
	 * a new state will create.  
	 * @param name - name of the state
	 * @param in - actions that we take when switch to this state
	 * @param out - actions that we take when switch off this state
	 * @return a reference to the {@link UIState} object
	 * @note parameter {@link in} and {@link out} are only valid when create a new state
	 */
	public UIState ep(String name, Runnable in, Runnable out){
		UIState sx = states.get(name);
		if(sx == null){
			sx = new UIState(in, out, name);
			states.put(name, sx);
		}
		return sx;
	}
	/**
	 * Get the reference of a state. If the specified state does not exit,
	 * a new state will create.  
	 * @param name - name of the state
	 * @return a reference to the {@link UIState} object
	 */
	public UIState ep(String name){
		return ep(name, null, null);
	}
	/**
	 * Start transfer with a specified state.
	 * @param fistStateame - name of the state
	 */
	public void start(String fistStateame){
		UIState sx = states.get(fistStateame);
		if(sx == null) {
			throw new RuntimeException("Unknown state name:" + fistStateame);
		}
		switchTo(sx);
	}
	/**
	 * Declare all states (initialize them) to avoid null-reference;
	 * @param name - name of states
	 */
	public void eps(String... name) {
		for(final String sx : name) {
			if(states.containsKey(sx)) throw new RuntimeException("Duplicated state name: " + name);
			states.put(sx, new UIState(null, null, sx));
		}
	}
	/**
	 * Set the default exception target state
	 * @param targetState - the target state name
	 */
	public void exdef(String targetState) {
		exdef(D$EF$EX, targetState);
	}
	/**
	 * Set the specified exception target state
	 * @param exceptionName - name of the exception
	 * @param targetState - name of the target state
	 */
	public void exdef(String exceptionName, String targetState) {
		if(states.get(targetState) == null)
			throw new RuntimeException("Undefined exception state: " + targetState);
		exceptions.put(exceptionName, states.get(targetState));
	}
	/**
	 * Trigger default exception
	 */
	public void ex(){
		ex(D$EF$EX);
	}
	/**
	 * Trigger specified exception
	 * @param exceptionName - name of the exception
	 */
	public void ex(String exceptionName) {
		UIState target = exceptions.get(exceptionName);
		if(target != null) {
			switchTo(target);
		} else {
			throw new RuntimeException("Unknow exception:" + exceptionName);
		}
	}
	/**
	 * Raise a event
	 * @param event - event name
	 */
	public void event(String event){
		Set<Runnable> actset = events.get(event);
		if(event != null) for(Runnable rx : actset){
			rx.run();
		}
	}
	/**
	 * Operate these method, enable abilities to assign specified
	 * value depends on current state.
	 * @param m - some consumer method
	 * @return
	 */
	@SafeVarargs
	public final <T> VariantObject<T> with(Consumer<T>... m) {
		VariantObject<T> vx = new VariantObject<T>(m);
		vars.add(vx);
		return vx;
	}
	
	/**
	 * Judge whether this state is current state.
	 * @param s - the state
	 * @return
	 */
	private final boolean isCurrent(UIState s){ 
		return s == current;
	}
	/**
	 * Switch to another state
	 * @param next - next state
	 */
	private void switchTo(UIState next) {
		if(current != null)
			current.leave();
		current = next;
		for(@SuppressWarnings("rawtypes") final VariantObject vx : vars) {
			vx.onChange();
		}
		next.into();
	}
	/**
	 * Registry a transfer event
	 * @param eventName - name of the event
	 * @param r - action to take when transfer occurs
	 */
	private void regEvent(String eventName, Runnable r) {
		Set<Runnable> actset = events.get(eventName);
		if(actset == null) {
			actset = new HashSet<>();
			events.put(eventName, actset);
		}
		actset.add(r);
	}
	/**
	 * Each UIState instance stands for a state in DFA.
	 * It supports state-based and transfer-based action.
	 * @author FF
	 *
	 */
	public final class UIState {
		private final List<Runnable> runs = new ArrayList<>();
		private Runnable into, leave;
		public final String name;
		private UIState(Runnable into, Runnable leave, String name) {
			this.into = into;
			this.leave = leave;
			this.name = name;
		}
		/**
		 * Called when current state become this state.
		 */
		private final void into(){
			if(into != null) {into.run();}
			for(final Runnable rx : runs) rx.run();
		}
		/**
		 * Called when current state is no long this state.
		 */
		private final void leave(){
			if(leave != null) leave.run();
		}
		/**
		 * Create an edge triggers by a Button.
		 * @param method - addEventListener method of specified button
		 * @param next - name of next state
		 * @return this object
		 */
		public final UIState edge(ActionListenerAdder method, String next) {
			return edge(method, null, next);
		}
		/**
		 * Create an edge triggers by a Button.
		 * @param method - addEventListener method of specified button
		 * @param act - the action we want to take when transfer occurs
		 * @param next - name of next state
		 * @return this object
		 */
		public final UIState edge(ActionListenerAdder method, Consumer<ActionEvent> act, String next){
			final UIState nexts = states.get(next);
			if(nexts == null) throw new RuntimeException("State not found: " + next);
			method.addActionListener(e -> {
				if(isCurrent(this)) {if(act != null) act.accept(e);; switchTo(nexts); }
			});
			return this;
		}
		/**
		 * Create an edge triggers by a Button.
		 * @param method - addEventListener method of specified button
		 * @param act - the action we want to take when transfer occurs, which returns
		 * 				the name of next state
		 * @return this object
		 */
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
		/**
		 * Create an edge triggered by a event
		 * @param event - name of a event
		 * @param next - name of next state
		 * @return this object
		 */
		public final UIState edge(String event, String next) {
			return edge(event, null, next);
		}
		/**
		 * Create an edge triggered by a event
		 * @param event - name of a event
		 * @param act - action we take when transfer
		 * @param next - name of next state
		 * @return this object
		 */
		public final UIState edge(String event, Runnable act, String next){
			final UIState nexts = states.get(next);
			if(nexts == null) throw new RuntimeException("State not found: " + next);
			regEvent(event, ()-> {
				if(isCurrent(this)) {if(act != null) act.run(); switchTo(nexts); }
			});
			return this;
		}
		/**
		 * Create an edge triggered by a event
		 * @param event - name of a event
		 * @param act - action we take when transfer, which returns the name of the next state
		 * @return this object
		 */
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
		
		/**
		 * Create an edge triggered by timer
		 * @param sleepms - how many milliseconds will it work
		 * @param next - name of next state
		 * @return
		 */
		public final UIState jmpLater(int sleepms, String next) {
			return jmpLater(sleepms, null, next);
		}
		/**
		 * Create an edge triggered by timer
		 * @param sleepms - how many milliseconds will it work
		 * @param act - action we take when transfer
		 * @param next - name of next state
		 * @return
		 */
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
		/**
		 * Set action take when current state become this state
		 * @param act - the action
		 * @return this object
		 */
		public final UIState into(Runnable act) {
			this.into = act;
			return this;
		}
		/**
		 * Set action take when current state no long become this state
		 * @param act - the action
		 * @return this object
		 */
		public final UIState leave(Runnable act) {
			this.leave = act;
			return this;
		}
		/**
		 * Set a set of setter to one value when current state become this state
		 * @param val - value
		 * @param m - setter methods
		 * @return this object
		 */
		@SafeVarargs
		public final <T> UIState set(T val, Consumer<T>... m) {
			runs.add(() -> {for(Consumer<T> mx : m) mx.accept(val);});
			return this;
		}
	}
	/**
	 * An object indicate a set of setter method, which is designed for
	 * state-depended variables
	 * @author FF
	 *
	 * @param <T> - type of the variable
	 */
	public final class VariantObject<T>{
		private final HashMap<String, T> map = new HashMap<>();
		private final Consumer<T>[] consumers;
		private T def = null;
		@SafeVarargs
		private VariantObject(Consumer<T> ... c){ consumers = c; }
		/**
		 * Set value to a specified one under these states
		 * @param val - the value
		 * @param states - name of states
		 * @return this object
		 */
		public VariantObject<T> when(T val,String... states) {
			for(final String sx : states) {
				if(map.containsKey(sx))
					throw new RuntimeException("Duplicated case condition: " + sx);
				map.put(sx, val);
			}
			return this;
		}
		/**
		 * Set value to a specified one under all other states
		 * @param val - the value
		 */
		public void otherthan(T val) {
			def = val;
		}
		/**
		 * Called when current state changes
		 */
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

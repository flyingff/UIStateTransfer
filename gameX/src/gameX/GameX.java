package gameX;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.function.Consumer;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.UIManager;

import net.flyingff.commons.ui.UIStateTransfer;

public class GameX extends JFrame {
	private static final long serialVersionUID = 8710755681994681182L;
	private static final int PORT = 6666;
	private static final String stone = "石头", sessior = "剪刀", cloth = "布";
	// 用户界面部分
	private JLabel lblLink, lblStatus;
	private JButton btnStone, btnSessior, btnCloth, btnLink, btnWait;
	// 游戏逻辑所需变量
	private String choice = null, peerChoice = null;
	private Socket sEP = null;
	private ServerSocket ss = null;
	private PrintStream ps = null;
	
	// 构造函数，用于构造用户界面，添加事件监听器以及初始化游戏逻辑
	public GameX() {
		super("石头剪刀布");
		
		//初始化所需UI元素
		lblLink = new JLabel("");
		lblStatus = new JLabel("");
		btnStone = new JButton(stone);
		btnSessior = new JButton(sessior);
		btnCloth = new JButton(cloth);
		btnLink = new JButton("连接服务器");
		btnWait = new JButton("等待客户端");
		
		// 设定游戏流程
		UIStateTransfer t = new UIStateTransfer();
		// 声明所需的所有状态
		final String INIT = "初始化", WAITSERV = "等待服务器", WAITCLNT = "等待客户端"
				, LINKED = "已连接", READY = "就绪", WAITSELF = "对方已出拳"
				, WAITPEER = "等待对方出拳" , RESULT = "游戏结束", SUCCEED = "成功";
		// 声明状态以便于交叉引用
		t.eps(INIT, WAITSERV, WAITCLNT, LINKED, READY, WAITSELF, WAITPEER, RESULT);
		
		// 声明默认异常目标状态
		t.exdef(INIT);
		
		// 声明依赖于状态的属性动作
		t.with(btnCloth::setEnabled, btnStone::setEnabled, btnSessior::setEnabled)
			.when(true, WAITSELF, READY).otherthan(false);
		t.with(btnLink::setEnabled, btnWait::setEnabled)
			.when(true, INIT).otherthan(false);
		t.with(lblLink::setText)
			.when("连接状态：未连接", INIT).when("连接状态：连接中...", WAITSERV)
			.when("连接状态：等待中...", WAITCLNT).otherthan("连接状态：已连接");
		t.with(lblStatus::setText).when("游戏状态：等待对方", WAITPEER)
			.when("游戏状态：就绪", READY).when("游戏状态：对方已出", WAITSELF)
			.when("游戏状态：裁判中", RESULT).when("游戏状态：已连接", LINKED)
			.otherthan("游戏状态：未开始");
		t.with(this::setTitle).when("石头剪刀布 - 离线", INIT)
			.when("石头剪刀布 - 正在等待客户端...", WAITCLNT).when("石头剪刀布 - 正在连接服务器...", WAITSERV)
			.otherthan("石头剪刀布 - 在线");
		
		// 声明状态转换条件及其动作
		t.ep(INIT).into(()->{
			}).edge(btnWait::addActionListener, ev -> {
					// 启动一个异步任务，完成后发送一个消息到状态转换管理器
					new Thread(()->{
						try {
							ss = new ServerSocket(PORT);
							sEP = ss.accept();
							ps = new PrintStream(sEP.getOutputStream());
							ss.close();
							ss = null;
							t.event(SUCCEED);
						} catch (Exception e) {
							errmsg("接受连接时出错：" + e.getLocalizedMessage());
							t.ex();
						}
					}).start();
			}, WAITCLNT).edge(btnLink::addActionListener, ev -> {
				new Thread(()->{
					try {
						sEP = new Socket(inputAddr(), PORT);
						while(!sEP.isConnected()) Thread.yield();
						ps = new PrintStream(sEP.getOutputStream());
						t.event(SUCCEED);
					} catch (Exception e) {
						errmsg("无法连接服务器：" + e.getLocalizedMessage());
						t.ex();
					}
				}).start();
			}, WAITSERV);
		t.ep(WAITSERV).edge(SUCCEED, LINKED);
		t.ep(WAITCLNT).edge(SUCCEED, LINKED);
		Consumer<ActionEvent> act =  e -> {
			choice = e.getActionCommand();
			try {
				ps.println(choice);
				ps.flush();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		};
		t.ep(LINKED).into(()->{
			// 开启工作线程
			new Thread(()->{
				try {
					Scanner sc = new Scanner(sEP.getInputStream());
					while(sc.hasNext()) {
						peerChoice = sc.nextLine();
						System.out.println("Peer Choice :" + peerChoice);
						if("exit".equals(peerChoice)) {
							t.ex();
						} else {
							t.event("peerMSG");
						}
					}
					t.event("exit");
					sc.close();
					sEP.close();
				} catch(Exception e) {
					errmsg("错误: " + e.getLocalizedMessage());
					t.ex();
				}
			}).start();
		}).jmpLater(1000, READY);
		
		t.ep(READY).edge(btnCloth::addActionListener,act, WAITPEER)
			.edge(btnSessior::addActionListener,act, WAITPEER)
			.edge(btnStone::addActionListener,act, WAITPEER)
			.edge("peerMSG", WAITSELF);
		t.ep(WAITPEER).edge("peerMSG", RESULT);
		t.ep(WAITSELF)
			.edge(btnCloth::addActionListener, act, RESULT)
			.edge(btnSessior::addActionListener, act, RESULT)
			.edge(btnStone::addActionListener, act, RESULT);
		
		t.ep(RESULT).into(()->{
			if(choice.equals(peerChoice)) {
				lblStatus.setText("游戏状态：平局");
			} else  {
				boolean win = false;
				switch(choice) {
				case stone:
					win = sessior.equals(peerChoice);
					break;
				case cloth:
					win = stone.equals(peerChoice);
					break;
				case sessior:
					win = cloth.equals(peerChoice);
					break;
					default:
						throw new RuntimeException("inner error");
				}
				lblStatus.setText("游戏状态：" + (win? "赢": "输"));
			}
		}).jmpLater(3000, READY);
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(sEP != null && !sEP.isClosed()) {
					try {
						ps.println("exit");
						ps.flush();
						Thread.sleep(100);
						ps.close();
						sEP.close();
					} catch (Exception ex) { }
				}
			}
		});
		
		// 设置窗体布局
		GroupLayout groupLayout = new GroupLayout(getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(groupLayout.createParallelGroup(Alignment.TRAILING)
						.addGroup(Alignment.LEADING, groupLayout.createSequentialGroup()
							.addComponent(lblStatus, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnStone)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnSessior)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnCloth))
						.addGroup(Alignment.LEADING, groupLayout.createSequentialGroup()
							.addComponent(lblLink, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnLink)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnWait, GroupLayout.PREFERRED_SIZE, 95, GroupLayout.PREFERRED_SIZE)))
					.addContainerGap())
		);
		groupLayout.setVerticalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
						.addComponent(lblStatus, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(btnStone)
						.addComponent(btnSessior)
						.addComponent(btnCloth))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
						.addComponent(lblLink, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(btnLink)
						.addComponent(btnWait))
					.addContainerGap())
		);
		getContentPane().setLayout(groupLayout);
		Dimension size = new Dimension(140, 20);
		lblLink.setPreferredSize(size);
		lblLink.setMinimumSize(size);
		lblStatus.setPreferredSize(size);
		lblStatus.setMinimumSize(size);
		
		// 设置窗体属性
		setResizable(false);
		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
		// 开启功能
		t.start(INIT);
	}
	
	// 输入服务器地址
	private String inputAddr() {
		return "localhost";
	}
	// 输出错误信息
	private void errmsg(String msg) {
		System.err.println(msg);
	}
	
	// 主函数
	public static void main(String[] args) {
		// 设置界面观感
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 生成一个该类实例
		new GameX();
	}
}

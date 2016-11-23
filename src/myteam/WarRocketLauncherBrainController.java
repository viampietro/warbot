package myteam;

import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.brains.brains.WarRocketLauncherBrain;
import edu.warbot.communications.WarMessage;

import java.util.List;

public abstract class WarRocketLauncherBrainController extends WarRocketLauncherBrain {
	public WarRocketLauncherBrainController() {
		super();
	}

	@Override
	public String action() {
		String messages = "";
		List<WarMessage> msgs = getMessages();
		
		List<WarAgentPercept> percepts = getPercepts();
		
		
		setDebugString("No target");
		
		
		for (WarMessage msg : msgs) {
			if (msg.getMessage().equals("Attack the enemy base")) {
				Vector2 exToEBase = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				Vector2 rLauncherToEx = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				// Vector2 baseToEBase = new Vector2(Float.valueOf(msg.getContent()[0]), Float.valueOf(msg.getContent()[1]));
				// Vector2 rLauncherToBase = VUtils.cartFromPolaire(msg.getAngle(), msg.getDistance());
				Vector2 rLauncherToEBase = rLauncherToEx.add(exToEBase);
				
				setDebugString("On my way to enemy base");
//				System.out.println("Angle launcherToEx = " + VUtils.polaireFromCart(rLauncherToEx).x 
//						+ ", angle launcherToEBase = " + VUtils.polaireFromCart(rLauncherToEBase).x);
				setHeading(VUtils.polaireFromCart(rLauncherToEBase).x);
			}
			
		}
		
		if (isBlocked())
			setRandomHeading();
		return move();
	}
}
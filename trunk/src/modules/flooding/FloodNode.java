package modules.flooding;

import util.Logger;
import util.Logger.LogLevel;
import data.AUserNode;
import data.annotation.ComponentInfo;
import data.annotation.Display;
import data.annotation.DisplayType;
import data.mod.IUserLink;
import data.mod.IUserPacket;

@ComponentInfo(name = "FloodNode")
public class FloodNode extends AUserNode {

	@Display(name = "flood sent", type = DisplayType.SHOW_SWING)
	public boolean floodSent = false;
	
	public void execute() {
		if (!floodSent) {
			for (IUserLink link : this.getConnectedLinks()) {
				send(new FloodPacket(this, 0), link, 1 + (long) (Math.random() * 2));
			}
			floodSent = true;
		} else {
			// no action
		}
	}

	private void receive(FloodPacket packet) {
		// check if the sender was me
		if (packet.getCreator().equals(this)) {
			// Logger.output(LogLevel.DEBUG, this, "rcv(OWN) => SINK");
		} else {
			// send on all links but from who the flood packet came a answer
			for (IUserLink link : this.getConnectedLinks()) {
				if (!packet.getLinkToSource().equals(link)) {
					// Logger.output(LogLevel.DEBUG, this, "rcv('" + packet.getCreator() +
					// "') => flood '" + link.getOtherNode(this) + "'!");
					send(new FloodPacket(packet), link, 1 + (long) (Math.random() * 2));
				} else {
					// Logger.output(LogLevel.DEBUG, this, "rcv('" + packet.getCreator() +
					// "') => no flood '" + link.getOtherNode(this) + "'!");
				}
			}
		}
	}

	public void receive(IUserPacket packet) {
		if (packet instanceof FloodPacket) {
			receive((FloodPacket) packet);
		} else {
			Logger.output(LogLevel.ERROR, this, "receive 'unknown' packet from " + packet.getSource());
		}

	}
}
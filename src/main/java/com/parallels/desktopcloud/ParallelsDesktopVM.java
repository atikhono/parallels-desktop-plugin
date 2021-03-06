/*
 * The MIT License
 *
 * (c) 2004-2015. Parallels IP Holdings GmbH. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.parallels.desktopcloud;

import hudson.Extension;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ListBoxModel;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;


public class ParallelsDesktopVM implements Describable<ParallelsDesktopVM>
{
	private static final ParallelsLogger LOGGER = ParallelsLogger.getLogger("PDVM");

	public enum PostBuildBehaviors
	{
		Suspend,
		Stop,
		KeepRunning,
		ReturnPrevState
	}
	
	public enum VMStates
	{
		Suspended,
		Paused,
		Running,
		Stopped
	}

	private final String vmid;
	private final String labels;
	private final String remoteFS;
	private transient String slaveName;
	private final ComputerLauncher launcher;
	private ParallelsDesktopNodeProperties nodeProperties;
	private transient boolean provisioned = false;
	private PostBuildBehaviors postBuildBehavior;
	private transient VMStates prevVmState;

	@DataBoundConstructor
	public ParallelsDesktopVM(String vmid, String labels, String remoteFS, ComputerLauncher launcher, String postBuildBehavior, ParallelsDesktopNodeProperties nodeProperties)
	{
		this.vmid = vmid;
		this.labels = labels;
		this.remoteFS = remoteFS;
		this.launcher = launcher;
		this.nodeProperties = nodeProperties;
		try
		{
			this.postBuildBehavior = PostBuildBehaviors.valueOf(postBuildBehavior);
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Error: %s", ex);
		}
		if (this.postBuildBehavior == null)
			this.postBuildBehavior = PostBuildBehaviors.Suspend;
		prevVmState = VMStates.Suspended;
	}

	public String getVmid()
	{
		return vmid;
	}
	
	public String getLabels()
	{
		return labels;
	}

	public String getRemoteFS()
	{
		return remoteFS;
	}

	public ComputerLauncher getLauncher()
	{
		return launcher;
	}

	public void setSlaveName(String slaveName)
	{
		this.slaveName = slaveName;
	}

	public String getSlaveName()
	{
		return slaveName;
	}

	public ParallelsDesktopNodeProperties getNodeProperties()
	{
		return nodeProperties;
	}

	public void setNodeProperties(ParallelsDesktopNodeProperties nodeProperties)
	{
		this.nodeProperties = nodeProperties;
	}

	public void setProvisioned(boolean provisioned)
	{
		this.provisioned = provisioned;
	}

	public boolean isProvisioned()
	{
		return provisioned;
	}
	
	public String getPostBuildBehavior()
	{
		if (postBuildBehavior == null)
			return PostBuildBehaviors.Suspend.name();
		return postBuildBehavior.name();
	}

	public PostBuildBehaviors getPostBuildBehaviorValue()
	{
		return postBuildBehavior;
	}
	
	public static VMStates parseVMState(String state)
	{
		if ("stopped".equals(state))
			return VMStates.Stopped;
		else if ("paused".equals(state))
			return VMStates.Paused;
		else if ("running".equals(state))
			return VMStates.Running;
		else if ("suspended".equals(state))
			return VMStates.Suspended;

		return null;
	}

	public void setPrevVMState(VMStates state)
	{
		prevVmState = state;
	}

	public String getPostBuildCommand()
	{
		switch (postBuildBehavior)
		{
		case ReturnPrevState:
			switch (prevVmState)
			{
			case Paused:
				return "pause";
			case Running:
				return null;
			case Stopped:
				return "stop";
			default:
				return "suspend";
			}
		case KeepRunning:
			return null;
		case Stop:
			return "stop";
		default:
			return "suspend";
		}
	}

	void onSlaveReleased(ParallelsDesktopVMSlave slave)
	{
		setProvisioned(false);
	}

	void setLauncherIP(String ip)
	{
		try
		{
			Class<?> c = launcher.getClass();
			Field f = c.getDeclaredField("host");
			f.setAccessible(true);
			f.set(launcher, ip);
			f.setAccessible(false);
		}
		catch (Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Error: %s", ex);
		}
	}

	String getLauncherIP()
	{
		try
		{
			Class<?> c = launcher.getClass();
			Field f = c.getDeclaredField("host");
			f.setAccessible(true);
			String ip = (String)f.get(launcher);
			f.setAccessible(false);
			return ip;
		}
		catch (Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Error: %s", ex);
		}
		return null;
	}

	@Override
	public Descriptor<ParallelsDesktopVM> getDescriptor()
	{
		return Jenkins.getInstance().getDescriptor(getClass());
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<ParallelsDesktopVM>
	{
		@Override
		public String getDisplayName()
		{
			return "Parallels Desktop virtual machine";
		}

		public ListBoxModel doFillPostBuildBehaviorItems()
		{
			ListBoxModel m = new ListBoxModel();
			m.add(Messages.Parallels_Behavior_Suspend(), PostBuildBehaviors.Suspend.name());
			m.add(Messages.Parallels_Behavior_Stop(), PostBuildBehaviors.Stop.name());
			m.add(Messages.Parallels_Behavior_KeepRunning(), PostBuildBehaviors.KeepRunning.name());
			m.add(Messages.Parallels_Behavior_ReturnPrevState(), PostBuildBehaviors.ReturnPrevState.name());
			return m;
		}

		public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
			return Functions.getNodePropertyDescriptors(Slave.class);
		}
	}
}

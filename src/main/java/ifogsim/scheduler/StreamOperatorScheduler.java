package ifogsim.scheduler;

import java.util.List;
import java.util.Map;

import cloudsim.Pe;
import cloudsim.VmSchedulerTimeShared;
import cloudsim.VmSchedulerTimeSharedOverSubscription;
import cloudsim.sdn.overbooking.VmSchedulerTimeSharedOverbookingEnergy;

public class StreamOperatorScheduler extends VmSchedulerTimeSharedOverbookingEnergy{

	public StreamOperatorScheduler(List<? extends Pe> pelist) {
		super(pelist);
	}
}

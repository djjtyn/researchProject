package ifogsim.placement;

import java.util.List;
import java.util.Map;

import ifogsim.application.Application;
import ifogsim.entities.FogDevice;
import ifogsim.entities.PlacementRequest;

public interface MicroservicePlacementLogic {
    PlacementLogicOutput run(List<FogDevice> fogDevices, Map<String, Application> applicationInfo, Map<Integer, Map<String, Double>> resourceAvailability, List<PlacementRequest> pr);
    void updateResources(Map<Integer, Map<String, Double>> resourceAvailability);
    void postProcessing();
}

package ifogsim.entities;

/**
 * Created by Samodha Pallewatta
 */
public interface LoadBalancer {
    int getDeviceId(String microservice, ServiceDiscovery serviceDiscoveryInfo);
}

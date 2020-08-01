package matsimIntegrate;

import java.util.Map;

import javax.inject.Singleton;

import org.matsim.pt.router.TransitRouter;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import transitCalculatorsWithFare.TransitFareControlerListener;

public class DynamicRoutingModuleWithMaas extends DynamicRoutingModule{

	public DynamicRoutingModuleWithMaas(FareCalculator minibusFareCalculator, String MTRFareFilePath,
			String transferDiscountJson, String LRFareFilePath, String busFareJsonPath, String ferryFareJsonPath) {
		super(minibusFareCalculator, MTRFareFilePath, transferDiscountJson, LRFareFilePath, busFareJsonPath, ferryFareJsonPath);
	}
	
	/**
	 * This is a setting for directly use the fareCal to open the dynamic routing.
	 * @param fareCalMap
	 */
	public DynamicRoutingModuleWithMaas(Map<String, FareCalculator> fareCalMap) {
		super(fareCalMap);
	}

	@Override
	public void install() {
		if (getConfig().transit().isUseTransit()) {
			setupFareCalculator();
			// Bind the fare to new dynamic and fare calculator
			bind(TransitRouter.class).to(TransitRouterFareDynamicMaasImpl.class);
			bind(TransferDiscountCalculator.class).to(NoTransferDiscount.class).in(Singleton.class);
			addControlerListenerBinding().to(MaaSFareControlerListener.class);
		}
	}
}

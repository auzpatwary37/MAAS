package matsimIntegrate;

import javax.inject.Singleton;

import org.matsim.pt.router.TransitRouter;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;

public class DynamicRoutingModuleWithMaas extends DynamicRoutingModule{

	public DynamicRoutingModuleWithMaas(FareCalculator minibusFareCalculator, String MTRFareFilePath,
			String transferDiscountJson, String LRFareFilePath, String busFareJsonPath, String ferryFareJsonPath) {
		super(minibusFareCalculator, MTRFareFilePath, transferDiscountJson, LRFareFilePath, busFareJsonPath, ferryFareJsonPath);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void install() {
		if (getConfig().transit().isUseTransit()) {
			setupFareCalculator();
			// Bind the fare to new dynamic and fare calculator
			bind(TransitRouter.class).to(TransitRouterFareDynamicMaasImpl.class);
			bind(TransferDiscountCalculator.class).to(NoTransferDiscount.class).in(Singleton.class);
		}
	}
}

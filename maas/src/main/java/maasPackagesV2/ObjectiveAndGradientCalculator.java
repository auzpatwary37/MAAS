package maasPackagesV2;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
/**
 * Main idea is to write a universal objective and gradient calculator taking into account the new fareLink Operator and maas operator construct
 * @author ashraf
 *
 */
public class ObjectiveAndGradientCalculator {

	public static Map<String,Double> calcRevenueObjective(SUEModelOutput flow, Set<String>operator, MaaSPackages packages){
		Map<String,Double> obj = operator.stream().collect(Collectors.toMap(k->k, k->0.));
		for(String o: operator) {
			//add the revenue from package price
			for(MaaSPackage pac:packages.getMassPackagesPerOperator().get(o)) {
				obj.compute(o,(k,v)->v+flow.getMaaSPackageUsage().get(pac.getId())*pac.getPackageCost());
			}
		}
		for(Entry<String,Map<String,Map<String,Double>>> timeMap:flow.getMaaSSpecificFareLinkFlow().entrySet()) {
			
		}
		
		return obj;
	}
	
}

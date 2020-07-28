package matsimIntegrate;

import java.math.BigDecimal;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import dynamicTransitRouter.transfer.TransferDiscountCalculator;

public class NoTransferDiscount implements TransferDiscountCalculator {
	/**
	 * It is a dummy function to ignore the transfer discount.
	 */

	@Override
	public double getInterchangeDiscount(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId,
			Id<TransitRoute> fromTransitRouteId, Id<TransitRoute> toTransitRouteId, String fromMode, String toMode,
			double lastStartTime, double lastEndTime, double thisAboardTime, double lastFare, double thisFare) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BigDecimal getExactInterchangeDiscount(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId,
			Id<TransitRoute> fromTransitRouteId, Id<TransitRoute> toTransitRouteId, String fromMode, String toMode,
			double lastStartTime, double lastEndTime, double thisAboardTime, double lastFare, double thisFare) {
		// TODO Auto-generated method stub
		return new BigDecimal(0);
	}

}

package rs.raf.banka2_bek.otc.mapper;

import rs.raf.banka2_bek.otc.dto.OtcContractDto;
import rs.raf.banka2_bek.otc.dto.OtcOfferDto;
import rs.raf.banka2_bek.otc.model.OtcContract;
import rs.raf.banka2_bek.otc.model.OtcOffer;
import rs.raf.banka2_bek.stock.model.Listing;

import java.math.BigDecimal;

public final class OtcMapper {
    private OtcMapper() {}

    public static OtcOfferDto toDto(OtcOffer offer, String buyerName, String sellerName,
                                    String listingCurrency, Long viewerUserId) {
        OtcOfferDto dto = new OtcOfferDto();
        dto.setId(offer.getId());
        Listing listing = offer.getListing();
        dto.setListingId(listing != null ? listing.getId() : null);
        dto.setListingTicker(listing != null ? listing.getTicker() : null);
        dto.setListingName(listing != null ? listing.getName() : null);
        dto.setListingCurrency(listingCurrency);
        dto.setBuyerId(offer.getBuyerId());
        dto.setBuyerName(buyerName);
        dto.setSellerId(offer.getSellerId());
        dto.setSellerName(sellerName);
        dto.setQuantity(offer.getQuantity());
        dto.setPricePerStock(offer.getPricePerStock());
        dto.setPremium(offer.getPremium());
        dto.setCurrentPrice(listing != null ? listing.getPrice() : null);
        dto.setSettlementDate(offer.getSettlementDate());
        dto.setLastModifiedById(offer.getLastModifiedById());
        dto.setLastModifiedByName(offer.getLastModifiedByName());
        dto.setWaitingOnUserId(offer.getWaitingOnUserId());
        dto.setMyTurn(viewerUserId != null && viewerUserId.equals(offer.getWaitingOnUserId()));
        dto.setStatus(offer.getStatus() != null ? offer.getStatus().name() : null);
        dto.setCreatedAt(offer.getCreatedAt());
        dto.setLastModifiedAt(offer.getLastModifiedAt());
        return dto;
    }

    public static OtcContractDto toDto(OtcContract contract, String buyerName, String sellerName,
                                       String listingCurrency, BigDecimal currentPrice) {
        OtcContractDto dto = new OtcContractDto();
        dto.setId(contract.getId());
        Listing listing = contract.getListing();
        dto.setListingId(listing != null ? listing.getId() : null);
        dto.setListingTicker(listing != null ? listing.getTicker() : null);
        dto.setListingName(listing != null ? listing.getName() : null);
        dto.setListingCurrency(listingCurrency);
        dto.setBuyerId(contract.getBuyerId());
        dto.setBuyerName(buyerName);
        dto.setSellerId(contract.getSellerId());
        dto.setSellerName(sellerName);
        dto.setQuantity(contract.getQuantity());
        dto.setStrikePrice(contract.getStrikePrice());
        dto.setPremium(contract.getPremium());
        dto.setCurrentPrice(currentPrice);
        dto.setSettlementDate(contract.getSettlementDate());
        dto.setStatus(contract.getStatus() != null ? contract.getStatus().name() : null);
        dto.setCreatedAt(contract.getCreatedAt());
        dto.setExercisedAt(contract.getExercisedAt());
        return dto;
    }
}

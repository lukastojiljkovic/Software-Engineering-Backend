package rs.raf.banka2_bek.option.mapper;

import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;

import java.math.BigDecimal;

/**
 * Mapper za konverziju izmedju Option entiteta i OptionDto.
 *
 * Koristi static metode (isti pattern kao ListingMapper u stock paketu).
 *
 * NAPOMENA: stockListing je LAZY loaded, pa ce pristup stock poljima
 * izazvati dodatni SQL upit ako se pozove van transakcije.
 */
public final class OptionMapper {

    private OptionMapper() {
        // Utility klasa — ne instancirati
    }

    /**
     * Mapira Option entitet u OptionDto.
     *
     * @param option       Option entitet za mapiranje
     * @param currentPrice trenutna cena osnovne akcije (za inTheMoney kalkulaciju)
     * @return popunjen OptionDto
     */
    public static OptionDto toDto(Option option, BigDecimal currentPrice) {
        if (option == null) return null;

        OptionDto dto = new OptionDto();
        dto.setId(option.getId());
        dto.setTicker(option.getTicker());
        dto.setOptionType(option.getOptionType().name());
        dto.setStrikePrice(option.getStrikePrice());
        dto.setPrice(option.getPrice());
        dto.setAsk(option.getAsk());
        dto.setBid(option.getBid());
        dto.setImpliedVolatility(option.getImpliedVolatility());
        dto.setOpenInterest(option.getOpenInterest());
        dto.setVolume(option.getVolume());
        dto.setSettlementDate(option.getSettlementDate());
        dto.setContractSize(option.getContractSize());
        dto.setCreatedAt(option.getCreatedAt());
        dto.setCurrentStockPrice(currentPrice);

        // Stock listing polja
        if (option.getStockListing() != null) {
            dto.setStockTicker(option.getStockListing().getTicker());
            dto.setStockName(option.getStockListing().getName());
            dto.setStockListingId(option.getStockListing().getId());
        }

        // In-the-money kalkulacija
        if (currentPrice != null && option.getStrikePrice() != null) {
            boolean itm = option.getOptionType() == OptionType.CALL
                    ? currentPrice.compareTo(option.getStrikePrice()) > 0
                    : currentPrice.compareTo(option.getStrikePrice()) < 0;
            dto.setInTheMoney(itm);
        }

        return dto;
    }
}

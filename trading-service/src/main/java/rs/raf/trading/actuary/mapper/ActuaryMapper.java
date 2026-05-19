package rs.raf.trading.actuary.mapper;

import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.actuary.dto.ActuaryInfoDto;
import rs.raf.trading.actuary.model.ActuaryInfo;

/**
 * Centralizovano mapiranje ActuaryInfo -> ActuaryInfoDto.
 *
 * NAPOMENA (copy-first ekstrakcija, faza 2c): monolitni mapper je citao podatke
 * o zaposlenom direktno preko {@code info.getEmployee()} ({@code @OneToOne}
 * veza). U trading-service-u {@code ActuaryInfo} drzi samo soft {@code employeeId};
 * ime/email zaposlenog razresava servisni sloj preko {@code BankaCoreClient} i
 * prosledjuje pre-razreseni {@link InternalUserDto} ovom mapperu.
 *
 * {@code employeeId} se uvek puni iz {@code ActuaryInfo} (lokalna kolona);
 * {@code employeeName}, {@code employeeEmail} i {@code employeePosition} samo ako je
 * {@code employee != null}. {@code position} nosi {@link InternalUserDto} (banka-core
 * ga puni za zaposlenog) — tako se zadrzava paritet sa monolitnim mapperom koji je
 * citao {@code Employee.getPosition()} kroz {@code @OneToOne} vezu.
 */
public final class ActuaryMapper {

    private ActuaryMapper() {}

    /**
     * @param info     aktuarski zapis (lokalni trading entitet)
     * @param employee pre-razreseni identitet zaposlenog iz banka-core; moze biti
     *                 {@code null} ako razresavanje nije uspelo ili nije trazeno
     */
    public static ActuaryInfoDto toDto(ActuaryInfo info, InternalUserDto employee) {
        if (info == null) return null;

        ActuaryInfoDto dto = new ActuaryInfoDto();
        dto.setId(info.getId());
        dto.setActuaryType(info.getActuaryType() != null ? info.getActuaryType().name() : null);
        dto.setDailyLimit(info.getDailyLimit());
        dto.setUsedLimit(info.getUsedLimit());
        dto.setNeedApproval(info.isNeedApproval());

        dto.setEmployeeId(info.getEmployeeId());
        if (employee != null) {
            dto.setEmployeeName(employee.firstName() + " " + employee.lastName());
            dto.setEmployeeEmail(employee.email());
            dto.setEmployeePosition(employee.position());
        }

        return dto;
    }
}

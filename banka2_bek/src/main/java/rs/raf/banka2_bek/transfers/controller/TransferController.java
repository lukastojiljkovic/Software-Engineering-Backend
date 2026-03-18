package rs.raf.banka2_bek.transfers.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.transfers.dto.*;
import rs.raf.banka2_bek.transfers.service.TransferService;

import java.util.List;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/internal")
    public ResponseEntity<TransferResponseDto> internalTransfer(@RequestBody TransferInternalRequestDto request) {
        return ResponseEntity.ok(transferService.internalTransfer(request));
    }

    @PostMapping("/fx")
    public ResponseEntity<TransferResponseDto> fxTransfer(@RequestBody TransferFxRequestDto request) {
        return ResponseEntity.ok(transferService.fxTransfer(request));
    }

    @GetMapping
    public ResponseEntity<List<TransferResponseDto>> getAllTransfers() {
        return ResponseEntity.ok(transferService.getAllTransfers(null)); // zameniti null sa client
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponseDto> getTransferById(@PathVariable Long transferId) {
        return ResponseEntity.ok(transferService.getTransferById(transferId));
    }
}
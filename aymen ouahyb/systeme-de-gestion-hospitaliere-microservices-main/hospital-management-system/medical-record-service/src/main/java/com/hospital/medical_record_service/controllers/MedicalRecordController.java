package com.hospital.medical_record_service.controllers;

import com.hospital.medical_record_service.clients.PatientDTO;
import com.hospital.medical_record_service.clients.PatientServiceClient;
import com.hospital.medical_record_service.entities.MedicalRecord;
import com.hospital.medical_record_service.repositories.MedicalRecordRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientServiceClient patientServiceClient;

    public MedicalRecordController(MedicalRecordRepository medicalRecordRepository, PatientServiceClient patientServiceClient) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.patientServiceClient = patientServiceClient;
    }

    @GetMapping
    public List<MedicalRecord> getAllMedicalRecords() {
        return medicalRecordRepository.findAll();
    }

    @GetMapping("/patient/{patientId}")
    public List<MedicalRecord> getRecordsByPatient(@PathVariable Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId);
    }

    @PostMapping
    @CircuitBreaker(name = "patientService", fallbackMethod = "fallbackCreateRecord")
    @Retry(name = "patientService")
    public MedicalRecord createMedicalRecord(@RequestBody MedicalRecord medicalRecord) {
        PatientDTO patient = patientServiceClient.getPatientById(medicalRecord.getPatientId());
        if (patient == null) {
            throw new RuntimeException("Patient not found");
        }
        return medicalRecordRepository.save(medicalRecord);
    }

    public MedicalRecord fallbackCreateRecord(MedicalRecord medicalRecord, Throwable throwable) {
        throw new RuntimeException("Service patient temporairement indisponible. Impossible de créer le dossier médical.");
    }
}

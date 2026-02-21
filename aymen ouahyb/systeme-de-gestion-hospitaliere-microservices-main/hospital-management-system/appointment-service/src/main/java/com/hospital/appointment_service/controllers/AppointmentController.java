package com.hospital.appointment_service.controllers;

import com.hospital.appointment_service.clients.PatientDTO;
import com.hospital.appointment_service.clients.PatientServiceClient;
import com.hospital.appointment_service.entities.Appointment;
import com.hospital.appointment_service.repositories.AppointmentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final PatientServiceClient patientServiceClient;

    public AppointmentController(AppointmentRepository appointmentRepository, PatientServiceClient patientServiceClient) {
        this.appointmentRepository = appointmentRepository;
        this.patientServiceClient = patientServiceClient;
    }

    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @GetMapping("/patient/{patientId}")
    public List<Appointment> getAppointmentsByPatient(@PathVariable Long patientId) {
        return appointmentRepository.findByPatientId(patientId);
    }

    @PostMapping
    @CircuitBreaker(name = "patientService", fallbackMethod = "fallbackCreateAppointment")
    @Retry(name = "patientService")
    public Appointment createAppointment(@RequestBody Appointment appointment) {
        PatientDTO patient = patientServiceClient.getPatientById(appointment.getPatientId());
        if (patient == null) {
            throw new RuntimeException("Patient not found");
        }
        return appointmentRepository.save(appointment);
    }

    public Appointment fallbackCreateAppointment(Appointment appointment, Throwable throwable) {
        throw new RuntimeException("Service patient temporairement indisponible, veuillez réessayer plus tard.");
    }
}

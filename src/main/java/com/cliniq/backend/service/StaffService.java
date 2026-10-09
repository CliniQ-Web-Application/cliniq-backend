package com.cliniq.backend.service;

import com.cliniq.backend.model.Staff;
import com.cliniq.backend.repository.StaffRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class StaffService {
    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public List<Staff> getAllStaff(){
        return staffRepository.findAll();
    }

    public Staff getStaffById(UUID id) {
        return staffRepository.findById(id).orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));
    }

    public Staff createPatient(Staff staff){
        return staffRepository.save(staff);
    }

    public Staff updateStaff(UUID id, Staff staffDetails) {
        Staff staff = getStaffById(id);
        staff.setName(staffDetails.getName());
        staff.setStaffRole(staffDetails.getStaffRole());
        staff.setEmail(staffDetails.getEmail());
        staff.setActive(staffDetails.getActive());
        staff.setDepartment(staffDetails.getDepartment());
        staff.setAvailabilityStatus(staffDetails.getAvailabilityStatus());
        return staffRepository.save(staff);
    }

    public void deleteStaff(UUID id) {
        if (!staffRepository.existsById(id)) {
            throw new RuntimeException("Staff not found with id: " + id);
        }
        staffRepository.deleteById(id);
    }
}

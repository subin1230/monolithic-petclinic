package org.springframework.samples.petclinic.customer.service;

import org.springframework.samples.petclinic.customer.db.OwnerRepository;
import org.springframework.samples.petclinic.customer.db.PetRepository;
import org.springframework.samples.petclinic.customer.model.Owner;
import org.springframework.samples.petclinic.customer.model.Pet;
import org.springframework.samples.petclinic.customer.model.PetType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class CustomerService {

    private final OwnerRepository owners;
    private final PetRepository pets;

    public CustomerService(OwnerRepository owners, PetRepository pets) {
        this.owners = owners;
        this.pets = pets;
    }

    public Collection<Owner> ownerByLastName(String lastName) {
        return owners.findByLastName(lastName);
    }

    public Owner ownerById(int id) {
        return owners.findById(id);
    }

    public Pet petById(int id) {
        return pets.findById(id);
    }

    public List<PetType> petTypes() {
        return pets.findPetTypes();
    }

    public void save(Owner owner) {
        owners.save(owner);
    }

    public void save(Pet pet) {
        pets.save(pet);
    }
}
/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.customer.controller;

import org.springframework.samples.petclinic.customer.model.Owner;
import org.springframework.samples.petclinic.customer.model.Pet;
import org.springframework.samples.petclinic.customer.model.PetType;
import org.springframework.samples.petclinic.customer.service.CustomerService;
import org.springframework.samples.petclinic.customer.service.PetFormValidator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Optional;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

    private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";
    private final CustomerService service;

    public PetController(CustomerService service) {
        this.service = service;
    }

    @ModelAttribute("types")
    public Collection<PetType> populatePetTypes() {
        return this.service.petTypes();
    }

    @ModelAttribute("owner")
    public Owner findOwner(@PathVariable("ownerId") int ownerId) {
        return this.service.ownerById(ownerId);
    }

    @InitBinder("owner")
    public void initOwnerBinder(WebDataBinder dataBinder) {
        dataBinder.setDisallowedFields("id");
    }

    @InitBinder("petForm")
    public void initpetFormBinder(WebDataBinder dataBinder) {
        dataBinder.setValidator(new PetFormValidator());
    }

    @GetMapping("/pets/new")
    public String initCreationForm(Owner owner, ModelMap model) {
        Pet pet = new Pet();
        owner.addPet(pet);
        PetForm petForm = toForm(pet);
        model.put("petForm", petForm);
        return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
    }

    @PostMapping("/pets/new")
    public String processCreationForm(Owner owner, @Valid PetForm petForm, BindingResult result, ModelMap model) {
        if (StringUtils.hasLength(petForm.getName()) && petForm.isNew() && owner.getPet(petForm.getName(), true) != null) {
            result.rejectValue("name", "duplicate", "already exists");
        }
        Pet pet = toEntity(petForm);
        owner.addPet(pet);
        if (result.hasErrors()) {
            petForm.setOwner(owner);
            model.put("petForm", petForm);
            return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
        }
        
        this.service.save(pet);
        return "redirect:/owners/{ownerId}";
    }

    @GetMapping("/pets/{petId}/edit")
    public String initUpdateForm(@PathVariable("petId") int petId, ModelMap model) {
        Pet pet = this.service.petById(petId);
        model.put("petForm", toForm(pet));
        return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
    }

    @PostMapping("/pets/{petId}/edit")
    public String processUpdateForm(@Valid PetForm petForm, BindingResult result, Owner owner, ModelMap model) {
        if (result.hasErrors()) {
            petForm.setOwner(owner);
            model.put("petForm", petForm);
            return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
        }
        
        Pet pet = toEntity(petForm);
        owner.addPet(pet);
        this.service.save(pet);
        return "redirect:/owners/{ownerId}";
    }

    private Pet toEntity(PetForm petForm) {
        Pet pet = new Pet();
        pet.setId(petForm.getId());
        pet.setName(petForm.getName());
        Optional<PetType> typeByName = this.service.petTypes()
            .stream()
            .filter(t -> t.getName().equals(petForm.getType()))
            .findFirst();
        typeByName.ifPresent(pet::setType);
        pet.setBirthDate(petForm.getBirthDate());
        return pet;
    }

    private PetForm toForm(Pet pet) {
        PetForm petForm = new PetForm();
        petForm.setId(pet.getId());
        petForm.setName(pet.getName());
        petForm.setType(pet.getType() != null ? pet.getType().getName() : null);
        petForm.setBirthDate(pet.getBirthDate());
        petForm.setOwner(pet.getOwner());
        return petForm;
    }

}

package net.bp4k.locationlens.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.bp4k.locationlens.dto.PlaceResponse;
import net.bp4k.locationlens.model.PlaceType;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

import org.springframework.http.HttpStatus;



@RestController 
@RequestMapping("api/v1/places")
public class PlaceController {
    // Will implement after the push
    
    @GetMapping("/search")
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<PlaceResponse> search() {
       //dummy information
       return List.of(
            new PlaceResponse("dummy-1", "Piedmont Park", "400 Park Dr NE, Atlanta, GA",
                33.7850, -84.3738, List.of(PlaceType.PARK)),
            new PlaceResponse("dummy-2", "Georgia Aquarium", "225 Baker St NW, Atlanta, GA",
                33.7634, -84.3951, List.of(PlaceType.ATTRACTION)),
            new PlaceResponse("dummy-3", "Ponce City Market", "675 Ponce De Leon Ave NE, Atlanta, GA",
                33.7725, -84.3655, List.of(PlaceType.RESTAURANT)));
    }

    
    @GetMapping("/{placeId}")
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String getPlaceDetails(@PathVariable("placeId") String placeId) {
        return new String();
    }
}

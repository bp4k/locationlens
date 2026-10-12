package net.bp4k.locationlens.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.bp4k.locationlens.model.PlaceType;
import tools.jackson.databind.json.JsonMapper;

class PlaceResponseTest {
    @Test
    void serializesTheExactFrontendContract() {
        PlaceResponse response = new PlaceResponse("ChIJ/place?123", "City Park", "1 Main St",
            40.123456789, -73.987654321, List.of(PlaceType.PARK, PlaceType.ATTRACTION));
        var mapper = JsonMapper.builder().build();
        assertEquals(mapper.readTree("""
            {"place_id":"ChIJ/place?123","name":"City Park","address":"1 Main St",
             "latitude":40.123456789,"longitude":-73.987654321,"types":["PARK","ATTRACTION"]}
            """), mapper.readTree(mapper.writeValueAsString(response)));
    }

    @Test
    void includesAllSupportedTypeNames() {
        var mapper = JsonMapper.builder().build();
        assertEquals(mapper.readTree("""
            ["RESTAURANT","BATHROOM","PARK","ATTRACTION","HOTEL","HOSPITAL","OTHER"]
            """), mapper.readTree(mapper.writeValueAsString(PlaceType.values())));
    }

    @Test
    void copiesTypesAndSupportsEmptyLists() {
        var source = new ArrayList<>(List.of(PlaceType.PARK));
        var response = new PlaceResponse("park", "Park", "", 0.0, 0.0, source);
        source.add(PlaceType.OTHER);
        assertEquals(List.of(PlaceType.PARK), response.types());
        assertThrows(UnsupportedOperationException.class, () -> response.types().add(PlaceType.OTHER));
        assertTrue(new PlaceResponse("park", "Park", "", 0.0, 0.0, List.of()).types().isEmpty());
    }
}

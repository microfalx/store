package net.microfalx.store.mapdb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.microfalx.lang.Identifiable;

import static net.microfalx.lang.StringUtils.toIdentifier;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestItem implements Identifiable<String> {

    private String id;
    private String firstName;
    private String lastName;
    private int age;

    @Override
    public String getId() {
        return id != null ? id : toIdentifier(firstName + ":" + lastName);
    }
}

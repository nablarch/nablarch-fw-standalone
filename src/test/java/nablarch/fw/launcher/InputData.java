package nablarch.fw.launcher;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * InputData
 */
@Entity
@Table(name = "InputData")
public class InputData {
   
    public InputData() {
    };
    
    public InputData(String id) {
		this.id = id;
	}

	@Id
    @Column(name = "ID", length = 3, nullable = false)
    public String id;
}
package nablarch.fw.handler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ハンドラテスト用バッチインプット
 */
@Entity
@Table(name = "HANDLER_BATCH_INPUT")
public class HandlerBatchInput {
    
    public HandlerBatchInput() {
    }

    public HandlerBatchInput(String id, String status) {
		this.id = id;
		this.status = status;
	}

	@Id
    @Column(name = "ID", length = 10, nullable = false)
    public String id;

    @Column(name = "STATUS", length = 1, nullable = false)
    public String status;
}

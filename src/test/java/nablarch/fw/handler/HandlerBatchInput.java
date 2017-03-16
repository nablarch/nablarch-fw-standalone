package nablarch.fw.handler;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

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

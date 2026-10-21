# Kế hoạch chi tiết: Xây dựng thế giới RPG hardcore VNMINE

> Tài liệu thiết kế và lộ trình triển khai. Đây chưa phải cam kết về số liệu cân bằng cuối cùng; các con số gameplay phải được cấu hình, thử nghiệm và điều chỉnh trước khi phát hành. Người chơi thao tác các tính năng RPG qua GUI; các file YAML và công cụ quản trị là giao diện cấu hình dành cho chủ server.

## 1. Tầm nhìn và mục tiêu sản phẩm

VNMINE là một thế giới Minecraft RPG có cốt truyện, gồm nhiều đại lục/world liên kết bằng cổng. Người chơi tạo nhân vật, chọn chủng tộc và class riêng, khám phá, nhận nhiệm vụ, chiến đấu với boss, mở khóa khu vực và góp phần khám phá vũ trụ truyện VNMINE.

Trải nghiệm phải giữ chất **hardcore sinh tồn**: tài nguyên có giá trị, môi trường nguy hiểm, chiến đấu đòi hỏi chuẩn bị và quyết định sai có hậu quả. Độ khó không được biến thành bất khả thi: nguy hiểm cần được báo hiệu, cơ chế cần được dạy, thất bại phải cho cơ hội phục hồi và tiến trình không được phụ thuộc vào vật phẩm quá hiếm hoặc nhóm đông người bắt buộc.

### 1.1 Mục tiêu bắt buộc

1. Có vòng chơi RPG hoàn chỉnh: tạo nhân vật → được hướng dẫn → nhận nhiệm vụ → khám phá/chiến đấu → nhận thưởng → mở nội dung mới.
2. Có nhiều đại lục riêng, mỗi đại lục gắn với một Minecraft world hoặc cấu hình world rõ ràng.
3. Có chủng tộc và class dành cho người chơi; không dùng nhầm class lính đánh thuê làm class nhân vật.
4. Có cốt truyện và nhiệm vụ được chia nhỏ, tham chiếu bằng ID ổn định và có thể mở rộng mà không biến một YAML thành kho chứa mọi loại dữ liệu.
5. Có lưu tiến trình riêng cho từng người chơi, an toàn qua restart và nâng cấp.
6. Người chơi sử dụng GUI làm phương thức tương tác chính; menu được điều hướng rõ ràng, có xác nhận cho lựa chọn khó đảo ngược.
7. Tất cả thông số gameplay mới có cấu hình mặc định hợp lý, có chú thích tiếng Việt và tiếng Anh, có giới hạn hợp lệ và được kiểm tra.
8. Các thay đổi không phá hỏng boss, loot, `story-id`, menu F, lính đánh thuê hoặc dữ liệu hiện hữu.

### 1.2 Ngoài phạm vi của đợt đầu

- Không xây ngay toàn bộ các đại lục và hàng chục class trước khi vòng chơi đầu tiên được kiểm chứng.
- Không bắt buộc xóa nhân vật/permadeath chỉ vì sản phẩm có nhãn hardcore.
- Không xây trình biên tập nhiệm vụ trong game ở phiên bản nền tảng; quản trị viên có thể định nghĩa nội dung bằng YAML.
- Không tạo hệ thống kinh tế, đấu giá, bang hội, PvP xếp hạng hoặc crafting riêng nếu chúng chưa phục vụ trực tiếp vòng chơi cốt lõi.
- Không chuyển mọi dữ liệu sang cơ sở dữ liệu lớn ngay lập tức; chọn lưu trữ theo quy mô và yêu cầu nhất quán.

## 2. Hiện trạng mã nguồn và các ràng buộc

Đây là cơ sở thiết kế rút ra từ mã nguồn đang có; cần đối chiếu lại ở đầu mỗi giai đoạn triển khai vì code có thể đã thay đổi:

- Plugin nằm trong `VnMineHardcore/`, dùng Paper API và Java 25; các hệ thống được khởi tạo/đăng ký trong `VnMineHardcore/src/main/java/com/vnmine/hardcore/VnMineHardcore.java`.
- `MercenaryManager` có nhiều menu GUI, tiến trình lính đánh thuê và xử lý `PlayerSwapHandItemsEvent` để chặn phím F, mở menu chính khi cấu hình `menus.main.enabled` và `menus.main.intercept-f-key` bật.
- `DeathHistoryManager` cho thấy mẫu GUI có holder riêng, định danh người xem, phân trang và xử lý click.
- `ConfigManager` đang nạp cấu hình, tạo/mở rộng tài nguyên YAML và nạp `story.yml`.
- `story.yml` hiện có `universe`, `chapters` và `artifacts`. Loader thực tế sử dụng `artifacts` cho lore item/giáp; chưa có tiến trình truyện hay nhiệm vụ dùng `universe`/`chapters`.
- Các file boss liên kết loot/giáp tới `story.yml` bằng `story-id`. Những ID đó là hợp đồng tương thích cần giữ hoặc di trú có kiểm soát.
- Plugin đã có boss theo cấp, loot boss, leaderboard, hình phạt tử vong, thirst/hunger, thiên tai, lính đánh thuê và các GUI quản trị/chơi hiện có. Đây là tài sản cần tích hợp có chủ đích, không thay thế đồng loạt.
- `plugin.yml` hiện mô tả các lệnh plugin; lệnh quản trị có thể giữ lại. Yêu cầu “chỉ qua GUI” áp dụng cho gameplay của người chơi, không buộc quản trị viên cấu hình mọi thứ bằng GUI.
- Cấu hình mặc định phải được hợp nhất mà không ghi đè giá trị do chủ server sửa. Cách merge hiện hữu cần được kiểm tra với cấu hình dạng danh sách và nhiều file trước khi mở rộng.

## 3. Nguyên tắc thiết kế gameplay

### 3.1 Hardcore nhưng công bằng

- Khó do lựa chọn, chuẩn bị, môi trường, tài nguyên và cơ chế boss; không khó do thông tin bị giấu hoặc ngẫu nhiên không thể phản ứng.
- Cảnh báo nguy hiểm trước khi gây sát thương lớn: âm thanh, hiệu ứng, vùng đánh dấu, hoạt ảnh hoặc thông báo GUI phù hợp.
- Có độ khó tăng theo đại lục/chương và đường lui hợp lý; vùng đầu tiên không được đòi trang bị endgame.
- Tử vong gây tổn thất có ý nghĩa nhưng có cơ chế quay lại trận, lấy lại vật phẩm/tiến trình hoặc hồi phục theo chính sách server.
- Không cộng dồn vô hạn các hình phạt hiện tại với phạt RPG. Phải mô phỏng tác động giữa death penalty, đồ rơi/mộ, thirst, boss và các hiệu ứng class.
- Cân bằng riêng cho chơi solo và tổ đội; không bắt buộc phải có healer/tank hoặc đội hình hoàn hảo để hoàn thành cốt truyện chính.
- Thử thách cuối game có thể cực khó, nhưng phải có tín hiệu học hỏi và khả năng thử lại.

### 3.2 Tân thủ có khả năng tiếp cận

- Nhân giới là điểm khởi đầu mặc định, có khu an toàn/hướng dẫn và nhiệm vụ nhập môn ngắn.
- Hướng dẫn chỉ dạy một khái niệm mỗi bước: di chuyển, sinh tồn, chiến đấu, dùng GUI, nhận nhiệm vụ, cổng.
- Chủng tộc/class hiển thị điểm mạnh, điểm yếu, vai trò, độ phức tạp và kỹ năng trước khi xác nhận.
- Nêu rõ lựa chọn nào khóa vĩnh viễn, lựa chọn nào đổi được và chi phí/điều kiện đổi.
- Có nhắc lại hướng dẫn từ menu Sổ tay; không ép người chơi phải đọc một đoạn hội thoại dài.
- Phần thưởng đầu game tạo cảm giác tiến triển, nhưng không làm mất thử thách hoặc khiến class khác kém giá trị.

### 3.3 Tiến trình dễ hiểu

- Mỗi nhiệm vụ có mục tiêu cụ thể, trạng thái hiện tại và chỉ dẫn bước tiếp theo.
- Mỗi đại lục có bảng mô tả độ nguy hiểm, đề xuất chuẩn bị và điều kiện mở khóa.
- Tài nguyên, XP, cấp nhân vật, cấp boss và cấp lính phải là các khái niệm khác nhau, có tên/GUI riêng để tránh nhầm.
- Hệ thống phải giải thích vì sao điều kiện chưa đạt thay vì chỉ khóa nút.

## 4. Mô hình thế giới và vũ trụ VNMINE

### 4.1 Khái niệm

- **Vũ trụ (universe):** lớp cốt truyện tổng thể, ví dụ “Biên Niên Rạn Nứt”.
- **Chương (chapter):** một phần của cốt truyện, có ID, thứ tự, điều kiện mở/hoàn tất và liên kết tới đại lục/nhiệm vụ.
- **Đại lục (continent/realm):** vùng nội dung có bản đồ, world, cổng, độ nguy hiểm, NPC, nhiệm vụ và boss.
- **World:** Minecraft world kỹ thuật. Một đại lục có thể dùng một world hoặc nhiều world; cấu hình phải phân biệt ID nội dung với tên world trong server.
- **Khu vực (region/area):** địa điểm trong đại lục, có điểm đến, mức nguy hiểm, nhiệm vụ/boss liên quan. Giai đoạn đầu chỉ cần metadata và điểm đến; chưa cần hệ region phức tạp.
- **Cổng (portal):** điểm GUI/điểm tương tác cho phép di chuyển giữa các đại lục theo điều kiện.
- **Nhân vật (character):** hồ sơ gameplay gắn với UUID người chơi ở phiên bản đầu; nếu về sau cho phép nhiều nhân vật trên một tài khoản thì phải thêm `characterId` vào mọi khóa dữ liệu.

### 4.2 Đại lục đề xuất theo tiến trình

Danh sách dưới đây là khung nội dung để viết truyện và thiết kế thử nghiệm; tên, vai trò, thứ tự và cốt truyện cuối cùng cần được chủ dự án duyệt. Không triển khai tất cả cùng lúc.

| Thứ tự đề xuất | Đại lục/giới | Vai trò gameplay | Tình trạng mở khóa gợi ý |
|---|---|---|---|
| 0 | Nhân giới | Khởi đầu, hướng dẫn, điểm an toàn, nhiệm vụ nhập môn | Mặc định |
| 1 | Yêu giới | Khám phá sinh vật/quy luật tự nhiên khác thường, thiên về thám hiểm | Sau chương nhập môn và nhiệm vụ mở cổng |
| 2 | Linh giới | Bí ẩn, dấu tích, cơ chế trạng thái/linh hồn và manh mối truyện | Sau một tuyến nhiệm vụ điều tra |
| 3 | Ma giới | Môi trường nguy hiểm, thử thách chiến đấu và quản lý tài nguyên | Sau khi người chơi chứng minh khả năng chuẩn bị |
| 4 | Thần giới | Nội dung endgame, boss/chương cao trào | Sau khi hoàn tất các mốc truyện trước |

Không khóa cổng chỉ theo cấp số. Dùng kết hợp trạng thái nhiệm vụ, cảnh báo độ khó, khả năng chuẩn bị và quyền truy cập. Tất cả các điều kiện cần hiển thị trong GUI.

### 4.3 Quy tắc world

- Mỗi cấu hình đại lục dùng `id` nội dung ổn định; `world-name` là tên world Paper đang tải.
- Hệ thống phải xác thực world tồn tại, điểm đến hợp lệ và thế giới không bị cấu hình nhầm.
- Không tự tạo/xóa world hoặc thay đổi cấu hình server rộng nếu chưa có yêu cầu quản trị rõ ràng.
- World chưa tải/không tồn tại phải được báo cho người chơi bằng thông báo thân thiện, ghi log quản trị và không làm mất nhiệm vụ/cổng.
- Cổng có điểm đến mặc định, điểm đến hồi hương và hành vi khi người chơi không có quyền vào.
- Cần định nghĩa chính sách giữ inventory, XP, hiệu ứng, cooldown, vị trí chết và hình phạt khi chuyển world.
- Teleport phải kiểm tra world/điểm đến trước khi cập nhật trạng thái tiến trình; thất bại giữa chừng không được đánh dấu cổng đã dùng hoặc nhiệm vụ đã hoàn thành.

## 5. Kiến trúc cấu hình và dữ liệu

### 5.1 Cấu trúc thư mục đích

Đây là cấu trúc mục tiêu; có thể điều chỉnh theo convention hiện hành nhưng phải giữ phân tách trách nhiệm:

```text
VnMineHardcore/src/main/resources/
  config.yml
  worlds/
    _settings.yml
    human-realm.yml
    ...
  story.yml
  story/
    universe.yml
    chapters.yml
    artifacts/
      ...
  quests/
    _settings.yml
    main/
      ...
    side/
      ...
    tutorial/
      ...
  races/
    _settings.yml
    ...
  player-classes/
    _settings.yml
    ...
  portals/
    _settings.yml
    ...
```

`dialogues/` chỉ tách riêng khi hội thoại đủ lớn hoặc cần nhiều nhánh. Mẫu cấu hình chung đặt `_settings.yml` nếu có thiết lập dùng chung. Mọi file mặc định phải có chú thích tiếng Việt và tiếng Anh về công dụng, giá trị mặc định và giới hạn hợp lệ.

Runtime data nằm ngoài resources/JAR, trong thư mục dữ liệu plugin:

```text
plugins/VnMineHardcore/
  players/
    <uuid>.yml
  backups/
    ...
```

Không đặt trạng thái hoàn thành nhiệm vụ của người chơi vào file nội dung mặc định.

### 5.2 Trách nhiệm từng nhóm cấu hình

- `story/universe.yml`: tên vũ trụ, premise, thứ tự chapter, glossary/nội dung chung.
- `story/chapters.yml`: chapter dạng map theo ID, thứ tự, tiêu đề và tóm tắt; bổ sung prerequisite/quest link sau khi có schema quest.
- `story.yml` hiện tại: catalog artifact/lore boss; không đổi các `story-id` đã được phát hành trong giai đoạn tương thích.
- `story/artifacts/*.yml` chỉ cân nhắc khi catalog artifact lớn đến mức cần tách file; không di chuyển ở giai đoạn hiện tại.
- `worlds/*.yml`: world-name, tên GUI, mức nguy hiểm, spawn/điểm đến, chapter và cổng liên quan.
- `quests/**/*.yml`: mục tiêu, bước, điều kiện, phần thưởng, chuỗi tiên quyết và liên kết nội dung.
- `races/*.yml`: ưu/nhược, kỹ năng và điều kiện áp dụng.
- `player-classes/*.yml`: vai trò, kỹ năng, cooldown, scaling, giới hạn.
- `portals/*.yml`: điểm tương tác, điểm đến, điều kiện, chi phí nếu có và quyền quản trị.
- `config.yml`: bật/tắt tính năng tích hợp, menu/keybind, chính sách chung, giới hạn hiệu năng và fallback.

### 5.3 Định danh và tham chiếu

- ID mới dùng biểu thức `[a-z0-9]+(?:-[a-z0-9]+)*`: chữ thường ASCII, chữ số và dấu gạch nối đơn giữa các đoạn; không dùng tên hiển thị làm khóa.
- ID duy nhất trong từng loại dữ liệu (chapter, quest, world, race, class, skill, portal); tham chiếu phải ghi đúng trường/loại, không giả định ID giữa các loại là toàn cục.
- Không tái sử dụng hoặc đổi ý nghĩa ID đã phát hành. Nếu cần đổi ID, phải có alias/migration và giữ tương thích dữ liệu đã lưu.
- ID boss/artifact legacy có thể dùng quy ước cũ (ví dụ boss dùng `_`); không chuẩn hóa hoặc đổi `story-id` chỉ để khớp quy ước ID mới.
- Tham chiếu chéo dùng ID có kiểu rõ ràng, không sao chép lặp nội dung quan trọng.
- Mỗi loại nội dung có loader/validator riêng dựa trên schema đã chốt; không dựng registry tổng quát trước khi có nhiều loại dữ liệu cần dùng chung.
- Validator kiểm tra ID, kiểu dữ liệu, giới hạn, tham chiếu mồ côi, chuỗi prerequisite và vòng lặp mở khóa phù hợp với từng loại.
- Với cấu trúc map keyed-by-ID, phải kiểm tra parser có phát hiện khóa YAML bị lặp hay không; không giả định map sau parse còn giữ được thông tin duplicate.
- Lỗi cấu hình nội dung cần nêu tên file và đường dẫn YAML; không silently skip cả hệ thống.
- Có chính sách lỗi: cấu hình sai của một nhiệm vụ không làm crash server, nhưng nhiệm vụ đó phải bị vô hiệu hóa có log rõ; cấu hình cốt lõi như player data phải có fallback/backup an toàn.

### 5.3.1 Schema chapter đã chốt

- File nội dung: `story/chapters.yml`; mỗi chapter là một key dưới `chapters`, ví dụ `chapter-01`.
- `schema-version` nằm ở cấp cao nhất của file, hiện là số nguyên `1`. Mỗi loại file nội dung quản lý version độc lập.
- Một chapter v1 có đúng các trường cần thiết hiện tại: `order` (số nguyên dương, duy nhất trong file), `title` (chuỗi không rỗng) và `summary` (chuỗi không rỗng).
- `order` không cần liên tục; đây là thứ tự hiển thị, không phải ID hay điều kiện mở khóa.
- Loader sắp xếp theo `order`, kiểm tra ID/field và chỉ nạp các chapter hợp lệ; lỗi phải nêu đường dẫn cấu hình.
- Thay đổi cấu trúc không tương thích phải tăng `schema-version` và có kế hoạch migration; bổ sung tùy chọn tương thích ngược có thể giữ version. Loader hiện tại từ chối version không hỗ trợ thay vì diễn giải sai.
- `story.yml:universe.chapters` được giữ lại như văn bản legacy; dữ liệu chapter có ID trong `story/chapters.yml` là nguồn định nghĩa runtime mới. Artifact và `story-id` không bị di chuyển.

### 5.3.2 Chiến lược loader/validator

- Dùng loader riêng theo từng loại định nghĩa khi schema của loại đó được duyệt; hiện tại artifact có `StoryArtifactValidator`, chapter có `StoryChapterLoader`.
- Loader trả về dữ liệu đã validate theo model riêng của loại đó và log lỗi có file/path; dữ liệu sai bị loại khỏi registry của loại tương ứng, không làm sập plugin.
- Chỉ trích xuất helper chung khi có ít nhất hai schema thực sự dùng chung cùng một quy tắc; không tạo framework registry tổng quát cho world/quest/race/class trước khi các schema ấy tồn tại.
- ID map có thể bị YAML parser ghi đè nếu cùng key xuất hiện nhiều lần; phải có test/biện pháp strict parsing trước khi tuyên bố đã phát hiện duplicate key.

### 5.4 Lưu tiến trình người chơi

Schema sơ bộ cho một nhân vật:

```yaml
schema-version: 1
player-id: "<uuid>"
character:
  race: "<race-id>"
  player-class: "<class-id>"
  level: 1
  experience: 0
  home-continent: human-realm
progress:
  unlocked-continents: [human-realm]
  completed-chapters: []
  active-quests: {}
  completed-quests: []
  choices: {}
cooldowns: {}
```

Schema chỉ là ví dụ, không phải định dạng đã cố định. Cần quyết định:

- Đã chốt một nhân vật trên mỗi UUID; runtime profile không có slot hay `characterId`.
- XP/cấp nhân vật có thực sự cần trong vertical slice hay tiến trình mở khóa dựa vào quest/gear.
- Trạng thái nào phải đồng bộ ngay và trạng thái nào có thể ghi theo batch.
- Làm sao đảm bảo nhận thưởng chỉ một lần, kể cả khi server restart giữa lúc hoàn thành.
- Cách xử lý người chơi offline và UUID/name thay đổi.
- Giới hạn kích thước file, backup, phục hồi file lỗi và dọn dữ liệu theo chính sách.

Ban đầu YAML per-player có thể đủ nếu lượng trạng thái nhỏ và lưu trên main thread có kiểm soát. Trước khi chọn, cần đánh giá kỹ tính nguyên tử, tần suất ghi và số người chơi đồng thời. Nếu dùng SQLite, thao tác I/O nặng phải chạy async, còn thay đổi Bukkit API vẫn trên main thread. Không triển khai hai backend cùng lúc ở đợt đầu.

### 5.5 Nâng cấp và tương thích

- Mỗi schema có `schema-version`; migration phải chạy theo thứ tự, có thể lặp an toàn và tạo backup trước khi chuyển đổi.
- Config defaults mới được merge mà không ghi đè tùy chỉnh của quản trị viên; cần kiểm thử riêng hành vi với map, list và file cấu hình sai.
- Giữ `story.yml`/các `story-id` hiện tại tương thích trong giai đoạn chuyển đổi. Có thể hỗ trợ đọc legacy rồi ghi cảnh báo, sau đó mới có migration tool hoặc giai đoạn ngừng hỗ trợ được thông báo.
- Không xóa file cũ tự động.
- Các ID nhiệm vụ/cổng/race/class đã lưu phải còn hợp lệ sau cập nhật; nếu bị bỏ phải có mapping hoặc fallback được ghi log.

## 6. Hệ thống nhân vật: chủng tộc và class

### 6.1 Chủng tộc

Mỗi chủng tộc khai báo:

- ID và thông tin GUI: tên, biểu tượng, mô tả ngắn/dài, độ phức tạp.
- Nội tại tích cực và giới hạn/điểm yếu tương ứng.
- Kỹ năng chủng tộc, điều kiện dùng, cooldown/tài nguyên và phản hồi GUI.
- Tương tác lore/quest nếu cần; không cho phép chủng tộc phá khóa nội dung chính.
- Chính sách chọn lại: khóa vĩnh viễn, đổi qua quest hoặc đổi trả phí; giá trị phải cấu hình và xác nhận trước khi áp dụng.

Nguyên tắc cân bằng: một lợi thế có ý nghĩa phải có giới hạn tương ứng; không nên tạo race “tối ưu mọi tình huống”. Chủng tộc nên đổi cách chơi hoặc lợi thế tình huống, không biến thành yêu cầu bắt buộc cho một class.

### 6.2 Class người chơi

Class nhân vật là subsystem riêng, không liên quan đến class lính đánh thuê:

- Mỗi class có archetype/role, độ khó, ưu/nhược và kỹ năng.
- Kỹ năng có trigger, cooldown, phạm vi, mục tiêu hợp lệ, scaling, feedback, persistence/cancel behavior.
- Cooldown và hiệu ứng cần dọn đúng khi logout, death, world change, reload và plugin disable.
- Kỹ năng area damage phải tái sử dụng luật thân thiện/địch, PvP, vùng bảo vệ và giới hạn mục tiêu hiện có.
- Nếu có tài nguyên riêng (mana/stamina), chỉ thêm sau khi xác định rõ nguồn hồi, giới hạn, GUI hiển thị và tương tác với hunger/thirst.
- Không để skill bỏ qua cơ chế nguy hiểm môi trường hoặc boss mà không có đánh đổi.

### 6.3 Tương tác giữa race và class

- Race và class được cân bằng độc lập trước, sau đó kiểm thử ma trận tổ hợp.
- Kỹ năng race không được nhân đôi vô hạn với kỹ năng class, đồ boss và buff mercenary.
- Mô tả GUI phải chỉ rõ hiệu ứng cộng dồn hay không cộng dồn.
- Cho người mới xem build mẫu như gợi ý, không khóa họ vào lựa chọn sai.
- Cơ chế đổi class/race cần tránh tạo vòng lặp đổi để khai thác cooldown, nhiệm vụ hoặc phần thưởng.

### 6.4 Phạm vi nội dung ban đầu

Để tránh nổ phạm vi, vertical slice chỉ cần một số lượng race/class nhỏ, đủ minh họa các vai trò khác nhau. Số lượng, tên và số liệu cụ thể được chốt sau khi:

1. Có mô tả lore cho từng lựa chọn.
2. Thiết kế được một tuyến quest nhập môn.
3. Kiểm tra khả năng biểu đạt kỹ năng bằng Paper API và kiến trúc hiện hành.
4. Có test cân bằng theo solo/party.

## 7. Quest, chapter và cốt truyện

### 7.1 Mô hình quest

Một quest nên hỗ trợ tối thiểu:

- `id`, tên, mô tả, chapter/continent và NPC/nguồn nhận.
- Điều kiện nhận: quest trước, race/class nếu thực sự cần, cổng/đại lục, trạng thái boss hoặc vật phẩm.
- Các bước có `id` và loại mục tiêu.
- Tiến độ: số hiện tại/tổng, trạng thái, vị trí/gợi ý bước tiếp theo.
- Điều kiện hoàn thành và phần thưởng.
- Điều kiện fail/abandon nếu có; mặc định không bắt buộc quest chính phải fail vĩnh viễn.
- Hành vi khi nhận lại, từ chối, bỏ dở, logout, death và server restart.
- Cờ mở khóa chapter/world/portal.

Mục tiêu ban đầu nên giới hạn ở các loại mà plugin xác nhận và có thể theo dõi tin cậy, chẳng hạn nói chuyện/nhận vật phẩm, tiêu diệt loại boss/mob có tag, thu thập vật phẩm có định danh, khám phá khu vực được cấu hình và hoàn thành quest khác. Không dùng tên hiển thị vật phẩm/mob làm định danh.

### 7.2 Phần thưởng và chống khai thác

- Quest reward chỉ cấp một lần theo idempotency key người chơi + quest + lần hoàn thành.
- Nếu completion và save không thể xảy ra nguyên tử, thiết kế trạng thái `reward-pending`/transaction để retry an toàn.
- Kiểm tra inventory đủ chỗ trước khi trao vật phẩm hoặc dùng mailbox/reward claim GUI.
- XP, vật phẩm, quyền mở khóa và teleport được trao theo một thứ tự có thể phục hồi.
- Không cấp vật phẩm quan trọng để tiếp tục quest theo cách có thể mất vĩnh viễn nếu người chơi chết; có thể có cơ chế nhận lại hạn chế/chi phí.

### 7.3 Nội dung cốt truyện

- `universe` và `chapters` hiện tại cần được chuyển thành dữ liệu có ID, không chỉ là danh sách dòng văn bản.
- Cốt truyện có thể chia thành lore được tìm thấy, hội thoại ngắn, journal update và sự kiện boss.
- Phân biệt lore tham khảo với dữ kiện gameplay: câu văn không được là nguồn duy nhất quyết định trạng thái mở khóa.
- Quest chính dẫn đường cho cốt truyện; quest phụ mở rộng nhân vật/đại lục và không khóa campaign chính.
- Hỗ trợ placeholder an toàn và giới hạn độ dài để GUI không tràn.
- Lựa chọn phân nhánh chỉ lưu khi có ý nghĩa thật; không tạo lựa chọn giả hoặc khiến người chơi mới khóa nội dung thiết yếu mà không cảnh báo.

## 8. Cổng, dịch chuyển và bản đồ

### 8.1 UX cổng

Menu Cổng/Bản đồ hiển thị:

- Danh sách đại lục theo thứ tự truyện.
- Trạng thái: đã mở, chưa mở, đang nguy hiểm/bảo trì.
- Mô tả, mức nguy hiểm, gợi ý chuẩn bị, quest cần hoàn thành.
- Điểm đến và xác nhận khi rời party/khu an toàn nếu có hệ party.
- Nút “Đi tới”, “Quay về Nhân giới” và “Xem điều kiện”.

Tương tác với cổng vật lý mở đúng trang đại lục; mọi thao tác cuối cùng thực hiện bằng GUI. Dùng `PersistentDataContainer`/ID nội bộ thay vì nhận dạng cổng qua tên item hoặc lore.

### 8.2 Chính sách gameplay cần quyết định

- Dịch chuyển có miễn phí hay tiêu tốn tài nguyên/vật phẩm.
- Cooldown cổng, giới hạn teleport khi combat, đang chết, đang làm boss hoặc đang ở menu.
- Có thể dịch chuyển party cùng nhau hay không.
- Người chơi có giữ trạng thái quest/party khi chuyển world.
- Cổng có hồi hương khẩn cấp không, và khi nào bị hạn chế.
- World boss/event có trạng thái chung hay cá nhân.

Thiết kế mặc định đề xuất: không cho thoát ngay khỏi combat/boss nếu điều đó phá luật chiến đấu, nhưng luôn có lối phục hồi sau khi chết hoặc khi teleport lỗi; GUI phải giải thích lý do bị từ chối.

## 9. GUI và phím tắt

### 9.1 Bản đồ menu

Menu F hiện là lối vào nhanh theo cấu hình; phát triển thành hub chính thay vì thêm hàng loạt phím nóng:

```text
Menu chính (F)
├── Nhân vật
│   ├── Hồ sơ / race / class / chỉ số
│   ├── Kỹ năng
│   └── Đổi class/race (nếu được phép)
├── Nhiệm vụ
│   ├── Đang làm
│   ├── Có thể nhận
│   └── Đã hoàn thành
├── Bản đồ & Cổng
│   ├── Đại lục
│   ├── Điều kiện mở khóa
│   └── Đi tới / hồi hương
├── Sổ truyện
│   ├── Chapter
│   ├── Lore đã tìm thấy
│   └── Hướng dẫn
└── Thiết lập
    ├── Phím tắt bật/tắt
    └── Thông báo/feedback
```

Các trang con có back button, phân trang, nút đóng và trạng thái rõ. Nút bị khóa phải giải thích điều kiện. Các thao tác nguy hiểm hoặc khó đảo ngược cần GUI xác nhận lần hai.

### 9.2 Chuẩn kỹ thuật cho GUI

- Mỗi loại menu có `InventoryHolder`/menu model riêng, không định danh bằng title.
- Holder chứa viewer UUID, menu type, page, target ID và context cần thiết; không giữ tham chiếu stale lâu dài.
- Chỉ xử lý click ở inventory plugin sở hữu; ngăn drag/shift-click/number-key/offhand exploit theo pattern hiện có.
- Revalidate điều kiện khi click; không tin trạng thái cũ chỉ vì nút đã hiển thị.
- Dùng service chung nếu có thể để chuẩn hóa item icon, trang, back, close, xác nhận, lore và quyền.
- GUI phải hiển thị nội dung an toàn khi cấu hình thiếu, nhưng log nguyên nhân cho quản trị viên.
- Có giới hạn tần suất mở menu và xử lý đóng menu khi logout/reload.

### 9.3 Phím F và các phím khác

- F tiếp tục mở menu chính theo `menus.main.enabled` và `menus.main.intercept-f-key`; giữ tùy chọn để người chơi/server không muốn mất swap tay phụ có thể tắt.
- Không chiếm thêm phím nóng toàn cục ngay từ đầu. Menu F là hub; người chơi có thể mở các phần từ GUI.
- Nếu sau này có phím tắt bổ sung, phải kiểm tra API/ProtocolLib thực tế, tính tương thích client, remapping và accessibility; không giả định server có thể đọc mọi phím tùy ý.
- Có fallback không cần phím nóng: vật phẩm/điểm tương tác/GUI menu có thể mở được hub.
- Không để F mở GUI khi đang thao tác inventory, trong trạng thái chết/respawn grace, hoặc bị chặn bởi một menu khác nếu hành vi đó gây xung đột; định nghĩa và test thứ tự listener.
- Cần test tương tác giữa F, offhand mercenary gear, respawn grace, GUI đang mở và các plugin khác.

## 10. Tích hợp với hệ thống hiện có

| Hệ thống hiện hữu | Quyết định tích hợp |
|---|---|
| BossEventManager | Gắn boss với đại lục/chapter/quest theo ID; giữ cơ chế boss/loot đang có, bổ sung event tiến trình qua API nội bộ |
| Boss loot và `story-id` | Không đổi nghĩa ID; tách lore khỏi trạng thái quest; kiểm tra tham chiếu khi khởi động |
| Boss leaderboard/reward | Giữ cách nhận reward hiện tại; không cho quest thưởng trùng leaderboard nếu không được cấu hình |
| MercenaryManager | Giữ class/XP lính độc lập; không dùng cấp lính làm cấp nhân vật; menu lính là một tính năng dưới hub nếu phù hợp |
| DeathPenaltyManager/DeathListener/GraveManager | Thiết kế chung chính sách chết và phục hồi; quest objective/reward không bị nhân đôi hoặc mất khi death |
| Thirst/Hunger/Environment | Dùng làm chất liệu hardcore theo đại lục nhưng tránh chồng sát thương không báo trước; cân bằng tutorial |
| PermissionManager | Quyền admin/debug tách khỏi quyền gameplay; người chơi không cần permission đặc biệt để dùng quest GUI |
| ConfigManager | Mở rộng theo loader/service rõ trách nhiệm; tránh nhồi toàn bộ world/quest/race vào một manager |
| TooltipManager/GUI hiện có | Tái sử dụng pattern chung nếu an toàn; tránh giao diện chồng và hướng dẫn trùng lặp |

Nên tạo event/domain API nội bộ có type safety, ví dụ `QuestObjectiveProgressEvent`, `ChapterUnlockedEvent`, `PortalTravelEvent`, thay vì để manager gọi trực tiếp lẫn nhau theo chuỗi phụ thuộc vòng. Chỉ trích xuất abstraction khi có nhiều consumer thực tế; không xây framework quá mức trước vertical slice.

## 11. Kiến trúc Java gợi ý

Cấu trúc package có thể bắt đầu gọn:

```text
com.vnmine.hardcore.rpg/
  RpgManager.java                 # vòng đời và wiring cấp tính năng
  config/
    RpgConfigLoader.java          # load + validate nội dung
  player/
    PlayerProfile.java
    PlayerProfileStore.java
    PlayerProfileService.java
  quest/
    QuestDefinition.java
    QuestService.java
    QuestObjectiveTracker.java
  world/
    ContinentDefinition.java
    ContinentService.java
    PortalService.java
  character/
    RaceDefinition.java
    PlayerClassDefinition.java
    CharacterService.java
  gui/
    RpgMenuListener.java
    ... holders/menu builders ...
```

Đây là gợi ý phân trách nhiệm, không yêu cầu tạo mọi lớp trước khi cần:

- Definition là dữ liệu cấu hình bất biến sau khi load.
- Service xử lý luật gameplay và xác thực điều kiện.
- Store chịu trách nhiệm đọc/ghi/migration/backup.
- Listener nhận sự kiện Bukkit, chuyển thành lệnh/domain action và không chứa toàn bộ business logic.
- Manager cấp cao đăng ký listener, task và đóng tài nguyên trong lifecycle plugin.
- Bukkit API chỉ gọi trên main thread; không serialize file lớn hoặc chặn server thread.

## 12. Lộ trình triển khai theo phase và cổng nghiệm thu

### Phase 0 — Chốt thiết kế và baseline

**Mục tiêu:** giảm quyết định mơ hồ trước khi code.

**Công việc**

1. Xác nhận định nghĩa hardcore: mất gì khi chết, khả năng lấy lại đồ, giới hạn hồi sinh và tương tác death penalty.
2. Xác nhận một/many character mỗi UUID.
3. Chốt đại lục đầu tiên, cốt truyện nhập môn, boss và điểm quay về.
4. Chốt có XP/cấp nhân vật ngay vertical slice hay chưa.
5. Chốt đổi race/class và quyền lựa chọn tân thủ.
6. Chốt solo-first hay party-required; đề xuất mặc định là solo có thể tiến triển, party chỉ cải thiện trải nghiệm.
7. Ghi baseline build/test hiện có trước khi sửa code; ghi nhận lỗi baseline riêng, không quy lỗi cũ cho tính năng mới.

**Nghiệm thu**

- Có sơ đồ vòng chơi và quyết định các câu hỏi trên.
- Không bắt đầu thêm nhiều race/class/world trước khi scope MVP được giới hạn.

### Phase 1 — Nền tảng cấu hình và validation

**Mục tiêu:** load nội dung RPG theo ID, phát hiện lỗi sớm, không phá config cũ.

**Công việc**

1. Thiết kế naming convention, schema version và thư mục resources.
2. Viết model bất biến cho world/chapter/quest/race/class/portal cần trong MVP.
3. Loader đọc từng file, validate type/range/ID/reference, tạo thông báo lỗi chỉ rõ đường dẫn.
4. Xác định cách merge default an toàn cho cấu trúc mới; test file cũ/tùy chỉnh/list.
5. Giữ loader hiện tại của `story.yml` và các ID artifact như adapter tương thích.
6. Thêm mẫu cấu hình song ngữ; mọi tham số có default và giới hạn.

**Nghiệm thu**

- File mặc định hợp lệ được load.
- Trùng ID, thiếu reference, world name sai, giá trị ngoài phạm vi được báo rõ; server không crash do một quest phụ sai.
- Không ghi đè thiết lập người quản trị.
- Nội dung artifact/loot hiện hữu vẫn resolve đúng.

### Phase 2 — Hồ sơ người chơi và lựa chọn nhân vật

**Mục tiêu:** có dữ liệu nhân vật bền vững và luồng tạo nhân vật GUI.

**Công việc**

1. Chọn YAML hoặc SQLite dựa trên đánh giá dữ liệu/tần suất ghi và concurrency.
2. Thiết kế profile schema, version, backup, migration, corruption recovery.
3. Implement load/save, cache theo UUID, save khi thay đổi quan trọng, quit và shutdown; tránh ghi lặp không cần thiết.
4. Tạo onboarding GUI: race → class → trang xác nhận → tạo profile.
5. Chống tạo profile lặp, click nhanh, disconnect giữa các bước và lỗi lưu.
6. Tạo GUI hồ sơ nhân vật hiển thị race/class/level (nếu có)/kỹ năng/quest count.

**Nghiệm thu**

- Người chơi mới hoàn tất lựa chọn, reconnect vẫn giữ lựa chọn.
- Người chơi có dữ liệu lỗi không bị reset im lặng; có backup/log/feedback phù hợp.
- GUI xác nhận các lựa chọn khó đổi.
- Chưa có quest/portal vẫn không làm menu nhân vật lỗi.

### Phase 3 — Vertical slice Nhân giới

**Mục tiêu:** hoàn thành một trải nghiệm từ spawn tới mở cổng.

**Công việc**

1. Khai báo Nhân giới và điểm vào/an toàn/hướng dẫn.
2. Tạo một chuỗi tutorial ngắn gồm sinh tồn, combat, GUI và một mục tiêu khám phá.
3. Tạo một quest chính với nhiều loại objective đã xác thực, lưu checkpoint.
4. Thêm NPC/điểm tương tác nhận và nộp quest qua GUI; không cần chat command cho người chơi.
5. Thêm journal/quest tracker trong menu hub.
6. Tạo phần thưởng đầu game hữu ích nhưng không bỏ qua độ khó.
7. Kiểm tra trường hợp death/relog/đổi world trong mỗi bước.

**Nghiệm thu**

- Người thử mới có thể biết mục tiêu tiếp theo mà không cần xem tài liệu ngoài.
- Quest không reset hoặc cấp thưởng lặp sau restart.
- Người chơi solo hoàn thành được.
- Death penalty/đói/khát không tạo soft-lock trong tutorial.

### Phase 4 — GUI hub và điều khiển thống nhất

**Mục tiêu:** biến menu F thành điểm truy cập gameplay RPG.

**Công việc**

1. Thiết kế menu chính và menu con theo sơ đồ mục 9.
2. Tích hợp các menu RPG mà không làm mất chức năng menu/lính hiện tại.
3. Chuẩn hóa holder, click validation, drag, pagination, back/close, confirmation và viewer ownership.
4. Thêm bật/tắt F và fallback mở menu bằng tương tác GUI hợp lý.
5. Xử lý xung đột F với offhand, mercenary gear, respawn grace, menu đang mở và plugin khác.
6. Thêm feedback cho hành động bị từ chối và lý do điều kiện chưa đạt.

**Nghiệm thu**

- Mọi luồng gameplay mới đi được bằng GUI.
- Không có thao tác GUI cho phép nhân bản item/nhận thưởng/mở khóa bằng shift-click, hotbar swap hoặc click ngoài holder.
- Tắt intercept F trả lại hành vi swap tay theo thiết kế hiện hành.

### Phase 5 — Đại lục thứ hai, portal và unlock

**Mục tiêu:** chứng minh mô hình đa world và tiến trình mở khóa.

**Công việc**

1. Chọn đại lục thứ hai có tương phản gameplay với Nhân giới.
2. Cấu hình world, spawn, mức nguy hiểm, quest/chapter và cổng.
3. Tạo GUI bản đồ/cổng, trạng thái khóa/mở và điều kiện chi tiết.
4. Implement teleport có xác thực world/điểm đến/combat/death/quest.
5. Ghi nhận unlock idempotent chỉ sau khi hành động cần thiết thành công.
6. Thêm quay về Nhân giới và fallback nếu điểm đến mất.
7. Kiểm tra party/group nếu có; nếu chưa có hệ party, ghi rõ portal cá nhân.

**Nghiệm thu**

- Quest unlock cổng đúng một lần, kể cả restart tại thời điểm chuyển trạng thái.
- World thiếu không làm mất unlock/quest; UI và log giải thích được.
- Người mới không thể vô tình teleport vào vùng nguy hiểm mà không thấy cảnh báo.

### Phase 6 — Hệ race/class và kỹ năng

**Mục tiêu:** mở rộng lựa chọn nhân vật trong phạm vi cân bằng kiểm soát được.

**Công việc**

1. Xác nhận danh sách race/class ban đầu và fantasy/lore của từng lựa chọn.
2. Định nghĩa kỹ năng với cooldown, scaling, target rules, PvP/protection rules, cleanup.
3. Thực hiện một class/race trước; đo khả năng tích hợp kỹ năng với event/listener hiện có.
4. Thêm các lựa chọn tiếp theo theo thứ tự ưu tiên, không nạp nội dung chưa được test.
5. Tạo GUI xem skill, cooldown, phạm vi, mô tả và nút thực hành nếu có.
6. Kiểm tra tương tác với boss, mercenary, armor set, potion, death penalty và world rules.

**Nghiệm thu**

- Skill không còn task/effect/cooldown sai sau death/logout/reload/disable.
- Không gây damage lên đồng minh hoặc vi phạm protection/PvP policy.
- Không có lựa chọn race/class thống trị rõ trong test nội dung cùng cấp.
- Class người chơi không làm thay đổi dữ liệu/class lính đánh thuê.

### Phase 7 — Mở rộng story, side quest, boss/chapter

**Mục tiêu:** tăng chiều sâu VNMINE sau khi nền tảng ổn định.

**Công việc**

1. Chuyển `universe`/`chapters` thành định nghĩa có ID và kết nối quest.
2. Phân loại quest chính, phụ, tutorial và sự kiện theo chapter/continent.
3. Liên kết boss hiện có với chapter/quest và objective event qua API nội bộ.
4. Thêm lore artifacts theo cách tương thích `story-id`.
5. Thêm hội thoại/lựa chọn phân nhánh chỉ khi có kết quả gameplay/story rõ.
6. Bổ sung journal cho lore đã tìm thấy và lịch sử quyết định.

**Nghiệm thu**

- Tắt/bật hoặc thiếu một quest phụ không chặn chapter chính.
- Boss kill không bị ghi nhận nhầm cho người không tham gia/đạt điều kiện nếu luật cần contribution.
- Artifact cũ vẫn có lore; chapter mới không cần sửa Java để khai báo.

### Phase 8 — Cân bằng, thử nghiệm và phát hành

**Mục tiêu:** đảm bảo hardcore công bằng, ổn định và vận hành được.

**Công việc**

1. Tạo test matrix: người mới/giàu kinh nghiệm, solo/party, từng race/class, từng world, death/reconnect.
2. Đo thời gian tutorial, tỷ lệ tử vong, thời gian hồi phục, tỷ lệ hoàn thành boss/quest, mức sử dụng portal.
3. Điều chỉnh theo config, ghi changelog và update README.
4. Kiểm tra backup/migration trên bản sao dữ liệu cũ.
5. Chạy thử trên server staging; phát hành theo một đại lục trước.
6. Thu thập phản hồi và chỉ mở đại lục kế tiếp sau khi xử lý lỗi blocker.

**Nghiệm thu**

- Không có soft-lock quest/cổng hoặc mất profile trong các kịch bản kiểm thử.
- Có thể khôi phục backup và migration không lặp thưởng.
- Tài liệu cho biết file cấu hình, điều kiện, quyền admin và hành vi mặc định.
- Không build JAR/phát hành khi chưa được yêu cầu; khi có yêu cầu build tính năng, tăng version đúng quy ước dự án.

### 12.1 Backlog triển khai thành các phần nhỏ

Mỗi đầu việc dưới đây nên được triển khai và kiểm chứng độc lập; không gộp nhiều hệ thống gameplay vào một thay đổi lớn. Các mục phụ thuộc chỉ bắt đầu khi prerequisite đã được chốt.

#### A. Thiết kế và hợp đồng dữ liệu

- [x] `DESIGN-01` Chốt chính sách tử vong, mất vật phẩm và phục hồi; ghi rõ tương tác với death penalty/grave hiện có.
- [x] `DESIGN-02` Chốt một hay nhiều nhân vật trên mỗi UUID, quy tắc đổi race/class và tiến trình cá nhân/party.
- [x] `SCHEMA-01` Chốt convention ID, cấu trúc chapter và version schema sau khi các quyết định Phase 0 hoàn tất.

#### B. Story và cấu hình

- [x] `STORY-01` Kiểm tra `artifacts.*.boss` trong `story.yml` có trỏ tới boss được cấu hình; giữ nguyên hành vi lore hiện có.
- [x] `STORY-02` Kiểm tra slug ID, tên item và lore không rỗng của artifact; cảnh báo kèm đường dẫn YAML, không đổi hành vi nạp lore.
- [x] `STORY-03` Thêm kiểm tra tự động cho artifact hợp lệ, boss không tồn tại và metadata tùy chọn bị thiếu/sai loại.
- [x] `STORY-04` Định nghĩa chapter có ID ổn định trong `story/chapters.yml` và loader riêng; giữ nguyên văn bản cũ cùng artifact IDs trong `story.yml`.
- [x] `CONFIG-01` Chốt chiến lược loader/validator riêng theo từng loại; artifact và chapter dùng loader/validator cụ thể, chưa dựng framework tổng quát.
- [x] `CONFIG-02` Kiểm thử merge mặc định không ghi đè cấu hình server; chốt giữ story legacy và không migration/xóa dữ liệu tự động.
- [x] `CONFIG-03` Kiểm tra parser và thêm strict handling cho duplicate YAML keys trong các map định nghĩa ID; loader từ chối file có khóa trùng trước khi nạp.

#### C. Hồ sơ và tạo nhân vật

- [x] `PROFILE-01` Chốt quyết định lưu hồ sơ người chơi bằng SQLite cho dữ liệu tiến trình, đồng thời giữ YAML cho nội dung tĩnh như race/class/world/portal definitions. Lý do: đây là game RPG có nhiều trạng thái theo thời gian, quest checkpoint, unlock đại lục, cooldown, progression cá nhân và khả năng mở rộng lên nhiều thế giới; YAML tốt cho config tĩnh nhưng không tối ưu cho update nhiều bản ghi theo thời gian và migration. Ở giai đoạn đầu, nếu server nhỏ/đơn giản, có thể dùng YAML tạm thời, nhưng với kế hoạch RPG dài hạn, SQLite là lựa chọn đúng để chuẩn bị cho nhiều player, nhiều world và nhiều loại tiến trình.
- [x] `PROFILE-02` Định nghĩa schema version, backup, phục hồi file lỗi và kiểm thử migration; đã xác minh migration v1→v2, recovery, backup/WAL, retention, retry, world unlock idempotent và quest progress/completion không trùng.
- [x] `CHARACTER-01` Tạo GUI chọn race/class có preview, xác nhận và chặn tạo/ghi đè hồ sơ lặp; race/class định nghĩa bằng YAML để quản trị viên tùy chỉnh.
- [x] `CHARACTER-02` Nạp hồ sơ vào session khi join, xóa cache khi quit; xác minh reconnect/restart không mất hoặc nhân đôi dữ liệu. Đã kiểm thử nạp lặp, khởi động lại database manager và từ chối tạo profile trùng mà không thay đổi dữ liệu.

##### 6.1.1 Quyết định lưu trữ hồ sơ

- `data/` hoặc `database/` giữ `player_profiles.db` với SQLite, không lưu biến trạng thái cá nhân trong nhiều file YAML.
- `player_profiles`: `uuid`, `player_name`, `selected_race`, `selected_class`, `level`, `exp`, `home_world`, `created_at`, `updated_at`, `is_active`.
- `player_quest_progress`: `uuid`, `quest_id`, `status`, `current_step`, `started_at`, `completed_at`, `reward_claimed`.
- `player_world_progress`: `uuid`, `world_id`, `unlocked`, `completed`, `last_visited`.
- `player_portal_progress`: `uuid`, `portal_id`, `unlocked`, `last_used`.
- Dữ liệu tĩnh như `races/*.yml`, `player-classes/*.yml`, `worlds/*.yml`, `quests/*.yml`, `portals/*.yml` vẫn ở YAML để dễ sửa nội dung bằng admin và không cần migration phức tạp.
- Mỗi lần cập nhật schema phải tăng `schema_version`; nếu file hoặc DB lỗi, phục hồi từ backup và không xóa progress chưa lưu.
- Trong môi trường nhỏ, mỗi UUID chỉ có một profile chính; nếu có nhiều nhân vật thì mỗi profile là một bản ghi độc lập với `profile_id`, nhưng không bắt đầu bằng nhiều character trong giai đoạn đầu.

##### 6.1.2 Chi tiết PROFILE-02

- `schema_version` đặt ở file/database cấp cao nhất. Với SQLite: thêm bảng `meta` với `key = 'schema_version'`, `value = '1'` hoặc hàng tương ứng.
- Mỗi bảng có `created_at`, `updated_at` để hỗ trợ audit và debug; `uuid` là khóa chính hoặc khóa phụ lệ thuộc vào bảng tương ứng.
- Mỗi lần thay đổi cấu trúc phải có migration function rõ ràng: `migrate_v1_to_v2`, không sửa dữ liệu bằng cách viết mặc định mù quáng.
- Backup: mỗi lần save/commit state, copy file SQLite sang `player_profiles.db.bak` hoặc `player_profiles.db.<timestamp>`, tối thiểu một bản backup mỗi 6 giờ hoặc khi có thay đổi lớn.
- Recovery: nếu DB lỗi hoặc bị corrupt, khôi phục bản backup gần nhất, nếu không có backup thì tạo profile mới với dữ liệu chặn lỗi hoặc giữ `player_profiles` cũ dạng read-only cho admin.
- `failed=retry` strategy: write transaction với `BEGIN IMMEDIATE`, cập nhật đủ trường, commit; nếu thất bại, rollback và log rõ trước khi trả lỗi tới player.
- Các test migration phải bao gồm: schema cũ không có cột `selected_class`, thêm world unlock mới, quest completed bị dupli, và lỗi giữa chừng khi save.
- Tất cả profile data phải được lưu trong transaction riêng, không gộp với config YAML hoặc save world state vì sẽ tăng rủi ro corruption.
- `PROFILE-02` hoàn tất: schema v2 và migration v1→v2 transactional; backup checkpoint WAL khi khởi động, trước khi ghi hồ sơ/migration và định kỳ; retention, retry lỗi SQLite busy/locked, recovery; transaction idempotent cho world unlock và quest start/step/completion. Mười lăm test manager bao phủ migration dữ liệu cũ, migration atomic khi lỗi, từ chối schema mới hơn, rollback, retry, retention, world unlock, quest completion trùng, recovery và đọc lại profile sau restart.
- `JOURNAL-01` đã hoàn tất với GUI hiển thị trạng thái/checkpoint/chỉ dẫn/thời gian hoàn thành/reward từ SQLite, mở từ hub F/`/vnmenu`, có nút đồng bộ tiến trình và nút tạo nhân vật khi thiếu profile. Nút đồng bộ gọi khởi tạo quest nên có thể tạo tiến trình cho profile hợp lệ chưa có quest; GUI dùng holder riêng, xác thực viewer, chặn click/drag. Mục menu có thể tắt bằng `rpg.quest-journal.enabled`. Reward XP nhân vật của tutorial được cộng nguyên tử cùng quest completion; reward dạng item/inventory chưa triển khai vì cần một thiết kế giao nhận bền vững riêng.

#### D. Vertical slice Nhân giới

- [x] `HUMAN-01` Khai báo world Nhân giới, spawn, safe zone và fallback; đã thêm loader/validator YAML, không tự tạo world, fallback tới world đã nạp, vùng an toàn chặn sát thương/PvP/quái thù địch và phá hoại địa hình. Năm test mới xác minh cấu hình, tọa độ bắt buộc, bán kính và khóa YAML trùng.
- [x] `QUEST-01` Implement quest tutorial tự bắt đầu cho profile đang hoạt động, objective `visit-realm-spawn` được validate và lưu active/completed trong SQLite; 5 test kiểm tra YAML, objective type, khóa trùng và biên bán kính. Reward và GUI được hoàn thiện lần lượt trong `QUEST-02` và `JOURNAL-01`.
- [x] `QUEST-02` Lưu checkpoint, hoàn thành và trao reward XP đúng một lần qua reconnect/restart. Trạng thái hoàn thành, `reward_claimed` và XP nhân vật được cập nhật trong một SQLite transaction; nếu ghi XP lỗi thì rollback cả completion. Hỗ trợ claim bù một lần cho completion cũ chưa claim. Đã xác minh restart/checkpoint, chống claim/completion trùng và rollback khi cập nhật reward thất bại.
- [x] `JOURNAL-01` Hiển thị quest đang làm, bước hiện tại, chỉ dẫn và trạng thái reward trong GUI; mở từ menu F/`/vnmenu`, có refresh/back và hướng dẫn tạo nhân vật nếu thiếu profile. Holder xác thực người xem, chặn thao tác inventory; mục menu có config bật/tắt. Có test định dạng lore cho quest active/completed.
- [ ] `DEATH-01` Kiểm thử death/recovery trong tutorial để không tạo soft-lock hoặc mất vật phẩm bắt buộc.

#### E. Hub GUI, cổng và kiểm thử mở rộng

- [ ] `GUI-01` Hoàn thiện hub F thành điểm điều hướng RPG (nhân vật và nhật ký nhiệm vụ đã được thêm trong các phần trước); bổ sung các entry RPG còn thiếu mà không làm thay đổi menu lính hiện tại.
- [ ] `GUI-02` Kiểm thử F bật/tắt, inventory click variants, respawn grace và offhand interaction.
- [ ] `PORTAL-01` Hiển thị đại lục đã khóa/mở và điều kiện truy cập trong GUI.
- [ ] `PORTAL-02` Thực hiện teleport có kiểm tra world/location/combat và chỉ commit unlock sau khi thành công.
- [ ] `BALANCE-01` Playtest Nhân giới solo với người mới; điều chỉnh cấu hình trước khi thêm đại lục mới.

`STORY-01` đến `STORY-04`, `DESIGN-01`, `DESIGN-02`, `SCHEMA-01`, `CONFIG-01`, `CONFIG-02`, `CONFIG-03`, `PROFILE-01`, `PROFILE-02`, `CHARACTER-01`, `CHARACTER-02`, `HUMAN-01`, `QUEST-01`, `QUEST-02` và `JOURNAL-01` đã hoàn tất. Bộ `PlayerProfileDatabaseManagerTest` có 18 test pass (15 test profile và 3 test checkpoint/reward); QUEST-02 kiểm thử restart, chống trao XP lặp, claim bù và rollback nguyên tử. JOURNAL-01 kiểm thử lore cho quest active/completed. Toàn bộ Maven test suite đã chạy thành công ngày 2026-10-21: 48/48 test pass. Phần tiếp theo theo thứ tự kế hoạch là `DEATH-01`, nhưng chưa bắt đầu; chờ xác nhận của người dùng. Mục này kiểm thử death/recovery trong tutorial để không tạo soft-lock hoặc mất vật phẩm bắt buộc. Reward hiện tại là RPG character XP lưu bền vững; item/inventory reward chưa hỗ trợ.

## 13. Kế hoạch kiểm thử chi tiết

### 13.1 Unit/logic tests

- Parse và validate mỗi definition: trường thiếu, loại sai, giá trị biên, ID trùng, reference mồ côi.
- Resolve dependency graph chapter/quest/portal; phát hiện cycle.
- Quest progression: objective cập nhật, checkpoint, fail/abandon, complete, reward exactly-once.
- Migration profile: schema cũ → mới, chạy lặp, lỗi giữa chừng, backup.
- Điều kiện mở cổng và thứ tự transaction.
- Cấu hình defaults được merge mà không ghi đè giá trị cũ.

### 13.2 Kiểm thử tích hợp Paper

- PlayerJoin/quit/death/respawn/world change và plugin reload/disable.
- GUI click trái/phải/shift/number key/offhand/drag/click ngoài.
- F key khi inventory đóng/mở, respawn grace, mercenary gear menu và keybind bị tắt.
- Teleport world tồn tại, world không tải, location sai, combat, chết, quest khóa.
- Damage/target rules với class skill, boss, PvP, protection và đồng minh.
- Restart server giữa các điểm save/reward/portal unlock.

### 13.3 Kiểm thử gameplay

- Người chơi mới không biết plugin: có thể tạo nhân vật, hiểu nhiệm vụ đầu và quay lại khu an toàn.
- Solo player hoàn thành được main path với chiến thuật hợp lý.
- Nhóm không nhân reward/quest progress ngoài thiết kế.
- Người chơi chết trước/sau khi nhận objective item không soft-lock.
- Mọi boss/đại lục đều báo hiệu nguy hiểm và cung cấp gợi ý chuẩn bị.
- So sánh các build race × class ở cùng gear/skill progression.

### 13.4 Hiệu năng/độ tin cậy

- Không quét mọi player/entity/world trên mỗi tick cho quest objective.
- Listener chỉ theo dõi event cần thiết; lọc nhanh theo world/ID/quest active.
- Hạn chế ghi file đồng bộ lặp lại; định lượng số lần save khi nhiều người cùng chơi.
- Dọn task/listener/cache khi quit/reload/disable; không giữ UUID/profile/menu vô hạn.
- Log không spam theo tick; phân cấp INFO/WARNING/SEVERE và có ngữ cảnh.

## 14. Bảo mật gameplay và chống khai thác

- Không tin dữ liệu từ tên/lore item hoặc inventory click; dùng PDC/ID và xác thực server-side.
- Mỗi reward/unlock có idempotency guard.
- Revalidate permission, quest state, inventory và world ngay khi thực hiện action.
- Chặn item duplication qua menu, reconnect, double click, shift click, drag, crash/restart.
- Không cho người chơi tự gửi command bắt buộc để tiến triển; mọi nút GUI gọi service đã xác thực.
- Admin debug command/menu cần permission riêng và không được lộ reward progression cho người thường.
- File YAML người chơi phải validate UUID, giới hạn collection/string và xử lý dữ liệu hỏng.
- Chỉ ghi log thông tin cần thiết; không đưa dữ liệu nhạy cảm hoặc nội dung không cần thiết vào log.

## 15. Tài liệu và quản trị server

Khi mỗi phase thêm tính năng, cập nhật:

- `README.md`: tổng quan RPG, luồng chơi, điều kiện, cấu hình, GUI và xử lý sự cố.
- `CHANGELOG.md`: thay đổi gameplay, migration, tương thích và lỗi đã sửa.
- `plugin.yml`: chỉ thêm command/permission quản trị khi thật cần; không tạo command người chơi làm đường bắt buộc thay GUI.
- YAML mặc định: song ngữ VI/EN cho từng tham số, default và valid range.
- Ví dụ cấu hình đại lục/quest/race/class/portal nhỏ, hợp lệ và kiểm thử được.
- Hướng dẫn backup/restore/migration dữ liệu người chơi.

## 16. Rủi ro chính và cách giảm thiểu

| Rủi ro | Tác động | Giảm thiểu |
|---|---|---|
| Scope quá lớn, thêm nhiều world/class trước khi loop đầu ổn định | Không hoàn tất hoặc khó cân bằng | Vertical slice Nhân giới + một cổng trước |
| `ConfigManager` phình to, liên kết manager vòng | Khó bảo trì và reload lỗi | Loader/service theo domain; chỉ trích xuất khi dùng thực tế |
| Migration config ghi đè tùy chỉnh | Mất cấu hình server | Test merge trên fixture cũ; backup; không ghi đè |
| Save player state không nguyên tử | Mất quest/reward hoặc nhận trùng | Schema version, backup, transaction/idempotency, restart tests |
| Chọn race/class vĩnh viễn làm tân thủ hối tiếc | Bỏ chơi hoặc yêu cầu admin reset | Preview đầy đủ, xác nhận, lựa chọn đổi có kiểm soát |
| Hardcore chồng phạt hiện có | Soft-lock, không thể phục hồi | Mô phỏng death/thirst/grave/quest; giới hạn penalty |
| GUI bị exploit hoặc khó dùng | Duplication, UX kém | Holder riêng, revalidation server-side, test mọi kiểu click |
| World/cổng cấu hình sai | Mất người chơi hoặc kẹt world | Validate lúc startup, safe return, không commit unlock khi teleport lỗi |
| Phụ thuộc API/client cho phím nóng | Không nhất quán giữa người chơi | Ưu tiên F hiện có, có config và fallback GUI |
| Lore tách khỏi gameplay | Người chơi không hiểu mục tiêu | Journal/quest link, nội dung ngắn, objective có ID |
| Mở rộng story phá `story-id` hiện có | Loot mất lore hoặc config không load | Legacy adapter, giữ ID, migration có cảnh báo |
| Nhiệm vụ dựa vào event không đáng tin | Progress sai hoặc không tăng | Objective types giới hạn, test tích hợp từng loại |

## 17. Chỉ số thành công

Các mục tiêu số cụ thể được chốt sau playtest; cần theo dõi ít nhất:

- Tỷ lệ người chơi mới hoàn tất onboarding.
- Thời gian trung vị tới khi hiểu được quest tracker/menu.
- Tỷ lệ hoàn thành quest chính đầu tiên và lý do dừng.
- Tỷ lệ tử vong theo khu vực/boss và thời gian trung bình phục hồi.
- Tỷ lệ người chơi bị soft-lock hoặc cần admin hỗ trợ.
- Tỷ lệ dùng class/race và hiệu quả theo cùng cấp gear/nội dung.
- Số lần lỗi config, reference thiếu, migration fail và save failure.
- Số lần portal teleport thất bại và nguyên nhân.
- Hiệu năng: thời gian tick/CPU, số lần save profile, log warning mỗi phiên.

Không tối ưu chỉ theo tỷ lệ thắng boss; một boss quá dễ hoặc quá khó đều có thể làm giảm trải nghiệm. Kết hợp telemetry tối thiểu, phản hồi người chơi và quan sát playtest.

## 18. Quyết định cần chốt trước khi triển khai

### 18.1 Đã chốt

- **Tử vong:** giữ hệ thống hiện tại: người chơi chết sẽ drop inventory; nếu Graves bật, vật phẩm được giữ trong mộ theo các giới hạn/thời hạn cấu hình hiện có; death penalty vẫn tích lũy và hồi phục theo thời gian sống sót. Không thêm phạt RPG chồng lên các cơ chế này nếu chưa được duyệt riêng.
- **Hồ sơ nhân vật:** một nhân vật trên mỗi UUID người chơi; profile không cần `characterId`/slot ở phiên bản đầu.
- **Đổi race/class:** được phép có giới hạn qua quest hoặc vật phẩm; điều kiện, chi phí và số lần đổi sẽ thiết kế trước khi triển khai, có preview/xác nhận và chống khai thác.
- **Tiến trình:** quest và mở khóa là cá nhân. Người chơi có thể hỗ trợ nhau trong nhóm, nhưng điều kiện mở khóa được ghi nhận cho từng người chơi theo quy tắc tham gia sẽ thiết kế cùng hệ quest.

### 18.2 Còn cần chốt

1. XP/cấp nhân vật có cần trong MVP hay chỉ dùng quest/chapter/gear?
2. Đại lục nào sẽ là nội dung đầu tiên sau Nhân giới, và cốt truyện chương đầu được duyệt ra sao?
3. Người chơi có thể teleport khi ở combat/boss không? Cơ chế hồi hương khẩn cấp là gì?
4. Server dự kiến có bao nhiêu người chơi đồng thời và mức dữ liệu cần lưu để chọn YAML hay SQLite?
5. F có tiếp tục mặc định chặn swap tay phụ hay mặc định tắt và để người chơi tự bật?
6. Có party/co-op trong MVP không, hay phiên bản đầu solo-first?

## 19. Thứ tự ưu tiên rút gọn

1. Chốt các quyết định ở Phase 0 và kiểm tra baseline.
2. Làm loader/validator và bảo toàn `story-id`.
3. Làm profile + chọn race/class bằng GUI.
4. Hoàn thành vertical slice Nhân giới: onboarding, quest, death recovery và journal.
5. Chuẩn hóa menu hub/F và kiểm thử tương tác GUI hiện có.
6. Làm một đại lục thứ hai cùng portal/unlock.
7. Cân bằng race/class/skill trên nội dung thật.
8. Liên kết boss, artifact và chapter; thêm quest phụ.
9. Playtest, sửa migration/soft-lock/duplication, phát hành theo từng đợt.
10. Mở rộng Yêu giới, Linh giới, Ma giới, Thần giới theo dữ liệu và kết quả playtest.

## 20. Tiêu chuẩn hoàn thành tổng thể

Nền tảng RPG VNMINE chỉ được coi là sẵn sàng mở rộng khi:

- Một người chơi mới dùng GUI tạo nhân vật, hiểu lựa chọn và bắt đầu chơi mà không cần hướng dẫn ngoài.
- Người chơi hoàn thành một tuyến quest, chết/relog/restart và tiếp tục đúng tiến độ.
- Quest thưởng đúng một lần; mở cổng đúng điều kiện.
- Có ít nhất hai đại lục/world kết nối được, có đường quay về và trạng thái khóa rõ.
- Race/class tạo khác biệt dễ hiểu nhưng không có lựa chọn bắt buộc để hoàn thành nội dung.
- Các luồng F/GUI không phá menu lính đánh thuê, respawn grace hoặc inventory.
- Cấu hình lỗi được báo cụ thể, dữ liệu người chơi có version/backup/migration, `story-id` cũ còn hoạt động.
- Kiểm thử solo, nhóm, death, reconnect, reload và plugin disable không gây soft-lock, mất dữ liệu hoặc reward duplication.
- README/changelog/config phản ánh chính xác hành vi; thay đổi được kiểm thử trước khi triển khai production.

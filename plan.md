# Kế hoạch chi tiết mở rộng RPG VNMINE

> Đây là kế hoạch thiết kế, chưa phải mô tả tính năng đã được triển khai. Các con số chiến đấu trong tài liệu là giá trị khởi điểm để playtest; khi đưa vào code phải cấu hình được, validate và cân bằng bằng kiểm thử. Tài liệu được xây dựng trên nền source hiện có: 15 class trong `player-classes.yml`, profile SQLite, hub F, quest hướng dẫn, journal, realm map và luồng teleport realm.

## 1. Mục tiêu và trạng thái nền

### 1.1 Mục tiêu

Hoàn thiện trải nghiệm RPG theo vòng chơi:

1. Người chơi chọn race/class và được hướng dẫn bởi NPC.
2. Nhận skill book, học kỹ năng dành cho class.
3. Tích lũy character EXP, lên level, nhận skill point và phân phối trong giao diện skill.
4. Gán skill vào quickbar, dùng Q mở bảng cast rồi cast bằng số hoặc click.
5. Nhận quest, mở chapter/realm và đi qua những portal được xây dựng, định danh, liên kết rõ ràng.
6. Tương tác với nhiều NPC có tên, vai trò, hội thoại, skin tùy khả năng kỹ thuật và GUI phù hợp.

### 1.2 Đã có trong source; không làm lại như tính năng mới

- 15 class và 5 race được khai báo trong `VnMineHardcore/src/main/resources/player-classes.yml` và `races.yml`.
- SQLite đã lưu `level`, `exp`, quest progress, world unlock và portal progress. Reward tutorial hiện cộng character EXP; chưa có hệ thống tự tính level-up/skill point/skill.
- Có GUI tạo nhân vật, hub mở bằng F, journal quest và chapter, realm map, điều kiện unlock và teleport an toàn theo realm.
- Có quest tutorial `first-steps`; hiện mục tiêu là tới điểm spawn, chưa có chuỗi quest/NPC hội thoại đầy đủ.
- ProtocolLib là dependency bắt buộc và code có packet listeners; nhưng chưa có hệ thống packet keybind cho người chơi.
- Chưa tìm thấy player skill book/progression, quản lý portal do admin tự đánh dấu/tạo/link, hoặc hệ NPC RPG tổng quát.

Khi bắt đầu mỗi phase phải kiểm tra lại trạng thái code và tái sử dụng manager/service hiện có thay vì giả định kế hoạch này luôn phản ánh source mới nhất.

## 2. Quyết định thiết kế đề xuất

1. **Dữ liệu tiến trình người chơi:** tiếp tục dùng SQLite hiện có; mở rộng bằng migration có backup/rollback, không lưu tiến trình người chơi vào YAML.
2. **Định nghĩa nội dung:** YAML có version riêng cho skills, skill books, portals, NPCs, dialogue và quests. ID ổn định, lowercase kebab-case.
3. **Level:** tái sử dụng `level`/`exp` trong profile; phân biệt rõ character EXP với vanilla XP và XP của mercenary.
4. **Skill unlock và skill upgrade là hai việc khác nhau:** sách/NPC/quest mở khóa skill; skill point dùng nâng rank của skill đã học.
5. **Quick cast giai đoạn đầu:** bấm Q mở GUI skill bar; số 1–9 chọn/cast slot trong khi GUI đang mở, click chuột là đường dự phòng. Chỉ triển khai bắt phím số toàn cục bằng packet sau khi xác minh client packet và tương thích ProtocolLib.
6. **Portal quản trị:** dùng selection bằng rìu gỗ kiểu WorldEdit làm tiện ích chọn vùng, nhưng plugin tự xử lý event, không phụ thuộc WorldEdit. Lệnh quản trị là nguồn thao tác rõ ràng và có thể kiểm thử.
7. **NPC giai đoạn đầu:** nhiều NPC được khai báo bằng ID riêng; NPC vật lý dùng entity Paper ổn định với PDC. Custom player skin là pha sau vì cần fake player/profile packet; không giả định villager có thể mang skin người chơi.
8. **Gameplay người chơi:** dùng GUI, NPC và tương tác trong world; lệnh xây dựng/quản trị chỉ dành cho permission quản trị viên.

## 3. Hệ thống level và skill point

### 3.1 Nguồn EXP và nguyên tắc lên cấp

- Dùng character EXP trong profile; không gọi `Player.giveExp()` và không trộn với EXP của lính đánh thuê.
- Nguồn EXP của MVP: quest đã validate, chapter/boss reward có điều kiện tham gia. Không thưởng EXP lặp theo mỗi hit; mob farm vô hạn chưa là nguồn mặc định.
- Hoàn tất quest và cộng EXP phải cùng transaction/idempotency như cơ chế quest reward hiện có.
- Level-up tự động khi EXP đạt ngưỡng; xử lý nhiều level nếu nhận reward lớn. Báo số level và skill point mới trong GUI/action bar.
- Level/EXP hiện có phải được migration/đọc tương thích; không reset profile.

### 3.2 Đường cong MVP đề xuất

- Level khởi điểm: 1; cap vertical slice: 30, sau này cấu hình mở rộng.
- EXP cần cho level kế tiếp: `100 + 50 × (level hiện tại - 1)`, tính theo cấu hình; mỗi lần lên 1 level nhận 1 skill point.
- Level 1 chưa có điểm miễn phí; chạm level 2 nhận điểm đầu tiên.
- Công thức và cap là mặc định playtest, không phải cân bằng cuối. Loader kiểm tra số dương, cap hợp lệ và không làm tràn số.
- Một lần nhận EXP có thể lên nhiều cấp; transaction lưu level, EXP, điểm chưa tiêu và lịch sử reward nguyên tử.

### 3.3 Skill point dùng ở đâu?

- Mở **Hồ sơ RPG → Kỹ năng → Cây kỹ năng** từ hub F hoặc `/vnskills`.
- Skill đã học hiển thị rank hiện tại, rank tối đa, điểm nâng cấp cần, tác dụng ở rank kế tiếp và điều kiện còn thiếu.
- Click skill → trang chi tiết; click “Nâng 1 rank” → xác nhận nếu thao tác không thể hoàn tác; chỉ trừ điểm khi DB update thành công.
- Skill point chỉ nâng rank skill đã học; không thay sách để mở skill, không dùng cho race/class/mercenary.
- MVP không hoàn tiền điểm. Thiết kế reset build chỉ sau này, qua NPC chuyên biệt, chi phí rõ ràng, xác nhận hai lần và audit.
- GUI luôn hiển thị tổng điểm đã nhận / đã dùng / còn lại; lỗi DB phải báo thất bại, không giả vờ đã nâng.

### 3.4 Dữ liệu cần lưu

- Profile hiện tại cần được rà soát: `level`, `exp` đã tồn tại nhưng chưa chứng minh có điểm skill.
- Migration dự kiến thêm `unspent_skill_points` vào profile hoặc bảng progression tương đương; lưu rank/unlock theo `(uuid, skill_id)`; lưu slot theo `(uuid, slot)`.
- Ràng buộc unique, foreign key, giới hạn rank, idempotency và transaction phải được kiểm tra bằng test SQLite.
- Không lưu cooldown dài hạn mặc định; cooldown runtime được dọn khi logout/death/restart. Chỉ persistence nếu thiết kế sau này yêu cầu rõ.

## 4. Danh sách class và kỹ năng đề xuất

Danh sách dưới đây bám 15 ID class đang có. Tên tiếng Việt, hiệu ứng và lore là nội dung đề xuất, chưa phải cam kết cân bằng. Mỗi class MVP có 2 skill chủ động + 1 nội tại; skill thứ ba/ultimate mở ở phase mở rộng. Chỉ triển khai 3 class đầu trong vertical slice, sau đó lần lượt các class còn lại.

| ID class (race) | Vai trò | Kỹ năng chủ động A | Kỹ năng chủ động B | Nội tại |
|---|---|---|---|---|
| `swordsman` (human) | Cận chiến cân bằng | **Kiếm Khí**: chém một đường ngắn theo hướng nhìn, gây sát thương vật lý lên tối đa số mục tiêu cấu hình; không xuyên tường. | **Thế Thủ**: giảm damage nhận trong thời gian ngắn, đổi lại giảm tốc di chuyển; không cộng dồn với chính nó. | **Luyện Kiếm**: bonus nhỏ cho đòn đánh thường sau khi chủ động giao chiến; không kích hoạt từ damage môi trường. |
| `guardian` (human) | Đỡ đòn, bảo vệ | **Khiên Chấn**: đánh bật mob gần trước mặt, làm chậm ngắn; không stun dài lên boss. | **Hộ Vệ**: tạo lớp giảm sát thương ngắn cho bản thân và đồng minh trong bán kính, giới hạn mục tiêu. | **Che Chắn**: khi đứng gần đồng đội được chọn, chia sẻ một phần damage theo cấu hình; tránh vòng lặp chia sẻ. |
| `ranger` (human) | Đánh xa, kiểm soát | **Mũi Tên Đánh Dấu**: projectile đánh dấu một mục tiêu hợp lệ, hiện dấu vết trong thời hạn. | **Bước Lùi**: lùi/né theo hướng an toàn, không xuyên block và không dịch chuyển vào vùng cấm. | **Ngắm Bắn**: bonus nhỏ sau khi đứng yên ngắm một khoảng tối thiểu; mất hiệu lực khi bị đánh. |
| `assassin` (elf) | Cơ động, burst | **Bóng Lướt**: dash ngắn theo hướng nhìn, không xuyên tường; có kiểm tra vùng bảo vệ và chống rơi vào void. | **Đòn Kết Liễu**: đòn đánh cận chiến mạnh hơn nếu mục tiêu bị đánh dấu/đang thấp máu; không one-shot boss. | **Ẩn Ảnh**: sau khi né thành công có cửa sổ ngắn tăng cơ động, không tàng hình vô hạn. |
| `archer` (elf) | Xạ kích chính xác | **Liên Xạ**: bắn loạt mũi tên skill với damage giảm theo từng mũi, có giới hạn số đạn. | **Tên Trói Chân**: projectile làm chậm mục tiêu thường; boss kháng/giảm thời lượng. | **Dây Cung**: tăng nhẹ hiệu quả bắn tầm xa sau khi nạp cung đủ lâu; không cộng dồn với enchant vượt trần. |
| `mage` (elf) | Phép thuật diện rộng | **Hỏa Cầu**: projectile có particle lửa, gây damage và burn ngắn; không phá block, không đốt công trình. | **Dịch Chuyển Tia Chớp**: blink tầm ngắn tới vị trí trống trong line-of-sight; thất bại an toàn nếu điểm đến bị chặn. | **Tiết Kiệm Mana**: giảm nhỏ resource cost theo rank, có mức sàn để không cast miễn phí. |
| `knight` (dwarf) | Kỵ sĩ, áp sát | **Xung Phong**: lao tới mục tiêu trong tầm, dừng trước vật cản và gây knockback giới hạn. | **Lời Thề Hộ Vệ**: giảm damage nhận và tăng chống knockback ngắn; không miễn nhiễm damage. | **Kỷ Luật Kỵ Sĩ**: bonus phòng thủ khi không dùng skill tấn công trong một khoảng thời gian. |
| `smith` (dwarf) | Hỗ trợ trang bị | **Tôi Luyện**: tạm tăng độ bền chiến đấu/giảm damage nhận cho bản thân hoặc đồng minh; không sửa độ bền vật phẩm vĩnh viễn trong combat. | **Búa Chấn Địa**: đánh vùng nhỏ quanh người chơi, gây damage thấp và làm chậm. | **Thợ Rèn Lão Luyện**: tăng nhẹ hiệu quả phòng thủ từ giáp, có cap và không cộng tuyến tính vô hạn. |
| `brute` (dwarf) | Cận chiến nặng | **Nện Đất**: đập vùng trước mặt, damage/knockback thấp lên boss hơn mob thường. | **Cơn Cuồng Nộ**: tăng damage trong thời gian ngắn nhưng giảm phòng thủ hoặc tiêu hao resource cao. | **Sức Nặng**: giảm knockback nhận vào, đổi lại giảm nhẹ tốc độ. |
| `berserker` (demon) | Đánh đổi máu lấy damage | **Huyết Trảm**: đòn đánh mạnh tiêu hao một lượng máu/resource có giới hạn; không thể tự gây chết do cost. | **Cuồng Chiến**: tăng tốc đánh/damage ngắn khi HP thấp, có cooldown và không kích hoạt lặp dây chuyền. | **Ý Chí Cuồng Nộ**: bonus có giới hạn khi HP thấp; không cộng thêm nếu nhiều nguồn cùng kích hoạt. |
| `warlock` (demon) | Sát thương/khống chế phép | **Hút Hồn**: projectile gây damage và hồi lượng nhỏ cho caster nếu trúng mục tiêu hợp lệ. | **Ấn Nguyền**: đặt debuff lên một mục tiêu trong thời gian ngắn; boss có thời lượng giảm. | **Dư Âm Hắc Thuật**: cast thành công có xác suất thấp hồi một phần resource, không reset cooldown. |
| `cultist` (demon) | Hỗ trợ, suy yếu | **Khế Ước Hắc Ám**: đổi một lượng resource/HP an toàn để tạo buff nhóm ngắn. | **Lời Nguyền**: giảm damage hoặc tốc độ mục tiêu; chọn một hiệu ứng làm MVP. | **Tụng Niệm**: khi ở gần đồng minh, tăng nhẹ hiệu quả hỗ trợ, không cộng dồn theo số cultist. |
| `sage` (spirit) | Hồi phục, support | **Linh Khí Hồi Phục**: hồi lượng máu cấu hình cho một mục tiêu đồng minh trong tầm, có diminishing/cap. | **Thanh Tẩy**: gỡ một số potion effect được whitelist; không xóa mọi debuff/boss mechanic. | **Tâm An**: tăng nhẹ hồi phục nhận được sau một khoảng không bị tấn công. |
| `seer` (spirit) | Trinh sát, cảnh báo | **Mắt Linh Giới**: reveal dấu vết/định hướng tới objective hoặc mục tiêu hợp lệ; không tiết lộ người chơi qua tường. | **Lời Báo Trước**: cảnh báo đòn đánh/vùng nguy hiểm gần đó bằng âm thanh/particle; không hủy damage. | **Trực Giác**: cảnh báo gần mob nguy hiểm theo bán kính/cooldown, không theo dõi toàn server mỗi tick. |
| `spirit-blade` (spirit) | Đấu kiếm linh hoạt | **Trảm Ảnh**: dash/chém ngắn, particle linh thể và damage đơn mục tiêu. | **Kiếm Trận**: tạo một vùng chém nhỏ theo hướng, số hit và thời gian đều giới hạn. | **Lưỡi Kiếm Linh**: sau skill thành công, đòn thường kế tiếp được bonus nhỏ; tiêu hao trạng thái sau một hit. |

### 4.1 Phân kỳ class

- **Vertical slice:** `swordsman`, `mage`, `sage` để kiểm thử cận chiến, projectile/utility và hồi phục. Class hiện có không bị xóa hoặc đổi ID.
- **Wave 2:** `guardian`, `ranger`, `assassin`, `archer`, `knight`, `brute`.
- **Wave 3:** `smith`, `berserker`, `warlock`, `cultist`, `seer`, `spirit-blade`.
- Một class chỉ được bật kỹ năng khi skill definition hợp lệ và class ID tồn tại. Loader báo lỗi chính xác nếu tham chiếu sai.
- Mỗi skill cần thử solo, party, boss, PvP policy, safe zone, spawn protection, death/respawn, world change và combat tag.
- Tất cả phạm vi, damage, cooldown, resource, duration, giới hạn mục tiêu và particle count có cấu hình song ngữ VI/EN, default và valid range.

## 5. Skill book và lore

### 5.1 Vật phẩm và cách học

- Sách là `ENCHANTED_BOOK` hoặc material cấu hình, có tên/lore hiển thị và dấu hiệu phát sáng tùy chọn.
- Định danh thật dùng PersistentDataContainer: namespace plugin + `skill_book_id`, `skill_id`, schema version; không tin tên/lore/material do người chơi tự đặt.
- Tác vụ học mặc định: người chơi cầm sách rồi **chuột phải**; UI xác nhận tên skill, class yêu cầu, level tối thiểu, mô tả và hậu quả tiêu thụ sách. Có thể học từ GUI nếu mở bằng NPC.
- Khi xác nhận, DB unlock skill trước; chỉ consume item sau kết quả thành công. Đảm bảo không nhân đôi khi double click, lag, reconnect hoặc DB exception.
- Sách không hợp class/đã học/thiếu level: không mất sách, trả thông báo giải thích.
- Skill book có thể là quest/NPC reward hoặc loot có xác suất cấu hình; không đặt sách class-specific hiếm đến mức khóa main quest.
- Sách chuyển nhượng hay bind người chơi là lựa chọn cấu hình; MVP đề xuất sách tradeable trước khi học, còn skill đã học gắn vĩnh viễn với UUID profile.

### 5.2 Định nghĩa và lược đồ

Tách `skills.yml` (skill gameplay) khỏi `skill-books.yml` (item, nguồn nhận và lore). Các ID không phụ thuộc vào tên hiển thị.

```yaml
skill-books:
  book-mage-fireball:
    skill-id: mage-fireball
    class-id: mage
    display-name: "Bút Tích Tro Tàn"
    material: ENCHANTED_BOOK
    min-level: 1
    consume-on-learn: true
    lore:
      - "Một nét mực đỏ nằm sâu dưới lớp bụi."
      - "Khi đọc lên, tro tàn nhớ lại hình dạng của lửa."
      - "Dạy kỹ năng: Hỏa Cầu"
    source:
      type: quest
      id: first-ember
```

### 5.3 Tuyển tập lore sách mẫu

- **Hỏa Cầu — Bút Tích Tro Tàn (mage):** “Một nét mực đỏ nằm sâu dưới lớp bụi. Khi đọc lên, tro tàn nhớ lại hình dạng của lửa.” Mở kỹ năng projectile Hỏa Cầu; lore nói rõ đây là phép gây cháy, không hứa phá hủy địa hình.
- **Thế Thủ — Sổ Tay Người Gác Cổng (swordsman):** “Trang giấy dày mùi sắt. Người viết không dạy cách thắng nhanh; ông dạy cách còn đứng vững sau nhát chém.” Mở kỹ năng phòng thủ Thế Thủ.
- **Linh Khí Hồi Phục — Thi Kệ Mạch Nguồn (sage):** “Nước không chống lại vết thương. Nó tìm khe nứt, rồi dẫn sự sống trở về.” Mở skill hồi phục; healer có giới hạn mục tiêu/cooldown.
- Lore là tài nguyên riêng theo book ID, cho phép dịch/localize; giới hạn số dòng và độ dài để GUI/inventory không vỡ.

### 5.4 Kiểm thử skill book

- Đổi tên sách vanilla không tạo được skill book.
- Copy item/shift-click/double click/relog không cho unlock hoặc tiêu thụ nhiều lần.
- DB lỗi không mất sách; lỗi được log kèm UUID/skill ID ở mức không lộ dữ liệu nhạy cảm.
- Migration giữ nguyên inventory; không âm thầm xóa sách cũ.

## 6. Quick cast, phím Q và phím khác

### 6.1 Sự kiện bàn phím: giới hạn cần tôn trọng

- Bukkit/Paper không cung cấp một event tổng quát cho mọi phím người chơi bấm.
- **Q** thường được server quan sát qua `PlayerDropItemEvent`; không phải `PlayerSwapHandItemsEvent`. Có thể hủy drop khi Q được cấu hình làm phím RPG, nhưng sẽ chiếm chức năng thả vật phẩm.
- **F** trong plugin hiện được nhận qua `PlayerSwapHandItemsEvent` và dùng mở hub; giữ tương thích mặc định.
- Trong inventory GUI, phím số có thể sinh `InventoryClickEvent` loại `NUMBER_KEY` khi con trỏ đang ở slot. Bắt buộc hủy thao tác chuyển vật phẩm trước khi dùng hotbar button làm lựa chọn skill.
- Đọc packet đổi hotbar ngoài GUI là hướng nâng cao, phụ thuộc ProtocolLib/client packet và có thể xung đột với thao tác hotbar thường. Chỉ chọn sau spike kỹ thuật trên đúng Paper/API/ProtocolLib đang hỗ trợ; không tuyên bố Q+1..9 toàn cục hoạt động chỉ dựa vào Bukkit.

### 6.2 UX quick cast MVP đề xuất

1. Người chơi bấm Q; listener nhận `PlayerDropItemEvent`, chỉ xử lý nếu cấu hình quick cast bật, profile đang active và người chơi không ở trạng thái bị chặn.
2. Hủy drop item và mở **Quick Cast** dạng inventory 9 slot. Nếu không đủ điều kiện hoặc đang ở giao diện không thể thay thế, không nuốt input âm thầm: giữ hành vi drop hoặc thông báo theo setting.
3. Mỗi slot hiển thị số, tên skill, icon, rank, resource, cooldown, class requirement và trạng thái “chưa gán/khóa/đang hồi”.
4. Người chơi rê chuột lên slot rồi nhấn số 1–9 hoặc click; listener nhận đúng holder/viewer, hủy mọi move/swap/drag và cast slot tương ứng. Click là fallback rõ ràng, không yêu cầu cài client mod.
5. Cast service xác thực lại profile, skill learned, slot binding, class, level, resource, cooldown, target, world/protection/PvP và combat state. GUI chỉ là input, không phải quyền xác thực.
6. Nếu đang dùng kỹ năng mất thời gian, GUI đóng sau cast thành công; thất bại giữ UI nếu phù hợp, hiển thị lý do và không tiêu hao cooldown/resource.
7. Cung cấp `/vnskills` và hub F như đường mở menu thay thế; nếu người chơi cần giữ Q để thả đồ, có cấu hình tắt intercept và dùng GUI/command.

### 6.3 Gán skill vào slot và UI

- Menu **Kỹ năng → Quickbar** hiển thị slot 1–9; chọn một skill đã học để gán, chọn “Gỡ” để xóa.
- Lưu mapping theo UUID và số slot; khi class đổi (nếu tính năng đổi class được bật), skill không hợp class chuyển sang trạng thái inactive, không xóa binding.
- GUI Skill Tree, Quickbar và Cast Bar là các màn hình riêng, có back/close, phân trang nếu cần, holder định danh và kiểm tra viewer.
- Không thay đổi inventory/hotbar vật phẩm thật để giả lập thanh kỹ năng.

### 6.4 Phím có thể tận dụng

| Phím/đầu vào | Cơ chế hiện có/khả thi | Quyết định đề xuất |
|---|---|---|
| F | Đang là hub qua swap tay phụ | Giữ hub; không gán skill trực tiếp |
| Q | Drop event Bukkit | Mặc định mở quick cast có thể bật/tắt; cảnh báo xung đột thả đồ |
| 1–9 | Number-key trong GUI; packet hotbar ngoài GUI cần spike | Dùng chọn skill khi quick cast GUI mở; ngoài GUI là phase nâng cao |
| Click trái/phải | InventoryClickEvent | Gán, xem chi tiết, xác nhận cast; hủy move item trong menu |
| Shift-click/drag/offhand | InventoryClick/Drag/Swap events | Hủy trong mọi GUI RPG |
| Sneak/Interact | Có Bukkit events nhưng ngữ cảnh dễ xung đột gameplay | Chưa gán skill mặc định; chỉ skill chuyên biệt sau thiết kế |
| Phím tùy chỉnh bất kỳ | Server không tự biết keybind tùy ý nếu client không gửi input chuyên dụng | Không cam kết; GUI/command là fallback chính thức |

### 6.5 Spike packet trước tính năng “Q rồi bấm số khi không cần rê chuột”

- Tạo prototype chỉ đọc/ghi nhận packet, không cast skill và không chặn input ngoài GUI.
- Xác minh packet nhận được trong game thật khi GUI mở/đóng, người chơi đổi hotbar, chết, reconnect và có ProtocolLib hiện tại.
- Đối chiếu `pom.xml` (khai báo 5.4.0) với kiểm tra version lúc runtime (đang yêu cầu 5.5.0-SNAPSHOT); giải quyết mismatch tương thích trước khi dựa thêm vào packet API.
- Nếu không chứng minh được độ tin cậy, giữ UX “mở GUI + rê chuột + phím số/click”; không thêm dependency hoặc client mod chỉ để đạt key chord.

## 7. Thiết kế hành vi skill

- `SkillDefinition`: ID, class, display name, icon, lore, category, trigger, target type, range, cooldown, resource cost, max rank, scaling, requirements, effect handler.
- Chỉ dùng effect handler đã được code/allowlist; YAML không chứa lệnh Java, Skript hoặc arbitrary command.
- MVP resource: không thêm mana/stamina mới ngay. Skill dùng cooldown và điều kiện rõ; sau playtest mới cân nhắc resource để không chồng hunger/thirst.
- Skill cast được xác thực ở service một lần, tiêu hao cooldown/resource nguyên tử theo thứ tự đã định; lỗi target hoặc protected zone không gây mất tài nguyên nếu không cast thành công.
- Friendly-fire, allies, PvP, boss resistance, spawn protection, safe zone và damage attribution dùng cùng policy hiện có; không bypass bằng skill.
- Projectile/area skill đặt PDC lên projectile hoặc state server-side; giới hạn entity count, bán kính, particle count và lifetime.
- Dọn task/projectile/effect/cooldown runtime khi death, quit, world change, reload và plugin disable.
- Passive không tạo task mỗi tick cho mỗi người chơi nếu có thể biểu diễn bằng event; mọi scheduled task phải có lifecycle/limit rõ.

## 8. Portal: xây dựng, đánh dấu và liên kết

### 8.1 Mô hình portal

- Một **portal ID** đại diện một đầu cổng tại một world. Hai đầu cổng có hai ID ổn định và `link` tới nhau; có thể cấu hình một chiều hoặc liên kết hai chiều.
- Mỗi đầu có: ID, tên hiển thị, world, vùng kích hoạt/frame, điểm đến chính xác, yaw/pitch, quyền bật/tắt, điều kiện quest/level, pair ID và policy combat.
- Điểm đến là tọa độ đứng an toàn, không chỉ là tọa độ khối cổng. Khi teleport phải tìm/kiểm tra feet/head space, world đã load, border, void, protection và respawn/combat guard.
- Portal RPG do quản trị viên tạo không thay thế realm map/unlock hiện có. Cổng có thể trỏ tới realm đã biết hoặc điểm khác trong realm; điều kiện unlock realm vẫn do RPG access service kiểm tra.

### 8.2 Rìu gỗ kiểu WorldEdit: có, nhưng là selection tool nội bộ

- Lệnh `/vnportal wand` cấp hoặc đánh dấu một WOODEN_AXE bằng PDC là công cụ chọn vùng; không dựa vào material/name để cấp quyền.
- Chuột trái lên block chọn `pos1`, chuột phải chọn `pos2`; thao tác chọn bị hủy để không phá/đặt block.
- Selection lưu tạm theo UUID, có world binding; xóa khi quit, reload, đổi world hoặc hủy thao tác. GUI/chat báo tọa độ và kích thước vùng.
- Kiểm tra permission, world, giới hạn cạnh/diện tích/cuboid và vùng bảo vệ/spawn-protection. Không cho chọn vùng vô hạn hoặc vùng của world khác.
- Đây chỉ là trải nghiệm tương tự WorldEdit, không tương thích lệnh/selection của WorldEdit và không cần dependency WorldEdit.
- Ngoài chọn vùng bằng rìu, có thể dùng `/vnportal pos1` và `/vnportal pos2` tại vị trí người chơi làm fallback không cần vật phẩm.

### 8.3 Quy trình khởi tạo portal cho admin

Ví dụ xây hai đầu tại `world` và `yeu`, ID `human-gate` và `yeu-gate`:

```text
/vnportal wand
<chuột trái block góc thứ nhất, chuột phải block góc đối diện của vùng cổng>
/vnportal create human-gate "Cổng Nhân Giới"
/vnportal setspawn human-gate
<đến vị trí đứng an toàn ở đầu bên kia>
/vnportal wand
<đánh dấu vùng cổng thứ hai>
/vnportal create yeu-gate "Cổng Yêu Giới"
/vnportal setspawn yeu-gate
/vnportal link human-gate yeu-gate --both
/vnportal info human-gate
/vnportal validate
```

Quy ước lệnh đề xuất:

| Lệnh | Tác dụng |
|---|---|
| `/vnportal wand` | Lấy selection tool có PDC |
| `/vnportal pos1`, `/vnportal pos2` | Đánh dấu vùng tại vị trí người quản trị nếu không dùng rìu |
| `/vnportal create <id> <tên...>` | Tạo portal từ selection hợp lệ; chưa tạo nếu ID trùng |
| `/vnportal setspawn <id>` | Lưu vị trí/yaw/pitch hiện tại làm destination an toàn |
| `/vnportal link <id-a> <id-b> [--both]` | Link một chiều hoặc hai chiều; hai chiều là mặc định đề xuất |
| `/vnportal unlink <id>` | Gỡ liên kết, có xác nhận nếu cổng đang hoạt động |
| `/vnportal list [page]`, `/vnportal info <id>` | Xem danh sách/thông tin, trạng thái validate và điều kiện |
| `/vnportal enable <id>`, `/vnportal disable <id>` | Bật/tắt không xóa định nghĩa |
| `/vnportal remove <id>` | Xóa sau xác nhận; từ chối nếu còn link hoặc cho biết hậu quả |
| `/vnportal validate` | Báo ID trùng, vùng sai, world/điểm đến thiếu, link mồ côi/cycle |

Tên có khoảng trắng phải được parse đúng; ID tuân thủ slug ổn định. Mỗi subcommand kiểm tra permission riêng hoặc permission quản trị portal chung. Lệnh không cung cấp cách teleport người chơi tùy ý để bypass access rule.

### 8.4 Trải nghiệm dùng portal trong game

- Khi người chơi đi vào vùng kích hoạt hoặc tương tác block trung tâm (phương án sẽ chốt sau prototype), portal hiện tên và điểm đến; tương tác lần hai/GUI xác nhận tránh teleport nhầm.
- Có particle/âm thanh nhẹ trong vùng, không spawn entity/block portal giả không giới hạn.
- Portal GUI hiển thị tên đầu đến, realm/world, yêu cầu level/quest, trạng thái đã mở/đang khóa và nút đi.
- Từ chối teleport trong combat/death/respawn grace theo policy cấu hình; chỉ ghi last-used/unlock sau teleport thành công.
- Một đầu chưa link, world chưa load hoặc destination lỗi sẽ hiện trạng thái disable kèm log; không silently teleport tới fallback sai.

### 8.5 Lưu trữ và an toàn portal

- Định nghĩa server-owned đặt trong `portals/<id>.yml` hoặc registry được quản lý theo cùng pattern loader; không ghi vào file cấu hình default người chơi sửa tùy ý.
- Mọi thao tác create/edit/write dùng validate trước, file tạm + atomic move/backup khi khả thi; không xóa dữ liệu nếu save thất bại.
- Loader kiểm tra duplicate ID/key, tọa độ hữu hạn, block/world height, volume tối đa, destination an toàn, link tham chiếu tồn tại và access condition.
- Dữ liệu `player_portal_progress` hiện có được đánh giá tái sử dụng cho unlock/last-used cá nhân, không nhầm với định nghĩa cổng vật lý.

## 9. NPC: loại, số lượng, GUI, tên và skin

### 9.1 Có thể tạo nhiều NPC không?

- Có. Mỗi NPC có ID duy nhất trong `npcs.yml`/`npcs/*.yml`; một server có nhiều NPC cùng loại, cùng template hoặc khác nhau.
- Template định nghĩa vai trò/hội thoại; instance định nghĩa display name, world/location, skin/entity profile, dialogue ID và các quest/shop/trainer links.
- Giới hạn runtime cấu hình `max-loaded-npcs-per-world` (mặc định đề xuất 100, range có validate); có thể tạo nhiều định nghĩa nhưng chỉ spawn trong world được phép. Cần đo hiệu năng trước khi nâng giới hạn.
- NPC gắn PDC `npc_id` và schema/version; interaction resolve ID từ PDC, không dựa vào custom name hay entity type đơn lẻ.

### 9.2 Bảy loại NPC đề xuất

Một NPC có thể mang nhiều capability (ví dụ quest giver kiêm gatekeeper); “loại” là bộ chức năng, không phải giới hạn một vai trò duy nhất.

1. **Hướng dẫn viên (tutorial):** hướng dẫn cơ chế, waypoint, menu và quest mở đầu.
2. **Người giao nhiệm vụ (quest giver):** nhận/hoàn tất quest, hiển thị prerequisite và reward.
3. **Huấn luyện viên (trainer):** giới thiệu class/skill; mở skill hoặc bán/trao skill book theo luật.
4. **Thương nhân/thợ rèn (merchant):** shop/đổi vật phẩm, upgrade; giao dịch phải có kiểm tra inventory và chống nhân đôi.
5. **Người giữ ký ức (lore keeper):** dialogue, chapter, artifact và nhật ký truyện.
6. **Người gác cổng (gatekeeper):** giải thích điều kiện và mở portal/realm; không tự bypass world access.
7. **Người chữa trị (healer):** dịch vụ hồi phục/giải trạng thái có giá/giới hạn, không xóa hardcore penalty trái luật.

### 9.3 Tạo, đổi tên và quản trị NPC

Lệnh admin dự kiến:

```text
/vnnpc create <id> <type> [template]
/vnnpc select
/vnnpc rename <id> <tên...>
/vnnpc move <id>
/vnnpc dialogue <id> <dialogue-id>
/vnnpc skin <id> <player-name|uuid>
/vnnpc info <id>
/vnnpc list [page]
/vnnpc despawn <id>
/vnnpc remove <id>
```

- `/vnnpc create` tạo instance tại vị trí admin nhìn/đứng, ghi định nghĩa bền vững; không cho duplicate ID.
- `/vnnpc select` hoặc chuột phải NPC mở admin editor khi có permission; người thường chỉ thấy tương tác gameplay.
- `/vnnpc rename` đổi tên hiển thị độc lập ID; escaping/color code/độ dài được validate để tránh nameplate abuse.
- `/vnnpc move` cập nhật vị trí; cleanup entity cũ trước khi respawn, không để clone sau reload.
- Có `NPC Manager GUI` cho admin: list, page, preview, enable/disable, edit name/dialogue/location/skin, remove có confirm. Lệnh vẫn là fallback và dùng được khi GUI lỗi.

### 9.4 Entity type và skin

- **MVP:** dùng Villager/vanilla entity an toàn theo loại NPC, AI được cấu hình/đóng băng phù hợp; display name đổi được. Có thể chọn mob model từ whitelist nếu type hỗ trợ.
- Skin người chơi **không áp trực tiếp lên Villager**. Muốn skin player phải tạo NPC dạng fake player/player profile và gửi spawn/metadata packet; cần xử lý tab-list cleanup, entity interaction/hitbox, skin profile texture, reconnect, plugin disable và version compatibility.
- Đề xuất triển khai skin player ở phase riêng bằng username/UUID/profile hợp lệ; cache kết quả, không nhận URL/texture tùy ý từ chat, không lưu secret/session token. Skin có thể trễ do client tải texture; tên hiển thị vẫn hoạt động nếu skin không resolve.
- Trước khi chốt fake-player implementation, làm spike so sánh ProtocolLib hiện có với Citizens/giải pháp chính thức tương thích. Ưu tiên không thêm dependency bắt buộc; nếu không ổn định, phát hành MVP không skin player và nêu rõ fallback, thay vì giả vờ đã hỗ trợ.
- Thay skin không đổi NPC ID, dialogue, quest hay vị trí; lỗi skin không được làm mất NPC hoặc crash plugin.

### 9.5 GUI người chơi

- Chuột phải NPC mở trang hội thoại có portrait/icon, tên, câu thoại ngắn và lựa chọn dạng nút.
- Quest giver có tab “Nhận”, “Đang làm”, “Hoàn tất”; nút hiển thị điều kiện/reward và trạng thái đã nhận.
- Trainer mở catalog skill phù hợp class; giải thích sách, level và cost trước khi giao dịch.
- Merchant dùng giao dịch server-side với confirm, kiểm tra chỗ túi/giá/stock atomically.
- Gatekeeper mở portal GUI có access status; lore keeper mở story/journal; tutorial NPC mở chỉ dẫn và waypoint.
- Mọi menu có holder riêng, UUID viewer, revalidation lúc click; chặn shift-click, hotbar swap, drag và stale menu. Có text/chat fallback nếu GUI bị tắt.

## 10. Tutorial, quest chain và particle waypoint

### 10.1 Hành trình tutorial đề xuất

Thời lượng mục tiêu 10–15 phút; có thể bỏ qua/hide sau khi hiểu cơ chế, không ép đọc hội thoại dài.

1. **Tỉnh giấc ở Nhân giới:** Tutorial Guide giới thiệu safe zone, hub F và journal. Tạo/kiểm tra profile; không reset nhân vật cũ.
2. **Chọn con đường:** người chơi xem lại class/race đã chọn và mở hồ sơ; NPC không cho đổi class miễn phí ngoài luật hiện có.
3. **Nhận sổ kỹ năng:** trainer trao skill book tương thích class, giải thích tên/lore, class restriction và cách học; nếu chưa có sách cho class thì dùng sách tutorial chung.
4. **Học và xem kỹ năng:** chuột phải sách; mở GUI Skill Tree, chỉ vị trí skill và điểm hiện có. Level đầu chưa cấp điểm cho tới khi đạt ngưỡng.
5. **Thử quick cast:** mở bằng Q, gán hoặc xác nhận slot, cast vào dummy/mục tiêu an toàn; kiểm tra cooldown và feedback. Nếu key conflict, hướng dẫn mở từ hub `/vnskills`.
6. **Tìm dấu mốc:** nhận objective đi tới NPC/địa điểm gần đó; waypoint particle/compass tùy bật. Objective hiện khoảng cách và hướng dẫn ngắn.
7. **Mở cổng đầu tiên:** quest yêu cầu tương tác gatekeeper/portal; giải thích điều kiện realm và combat restriction.
8. **Kết thúc tuyến nhập môn:** thưởng character EXP có exactly-once, cập nhật journal, mở quest/chapter kế tiếp và không cấp skill point trùng.

Quest hiện `first-steps` đang là objective tới spawn; mở rộng bằng quest ID/step mới có migration/data tương thích, không đổi nghĩa ID cũ hoặc nhân đôi reward.

### 10.2 Hướng dẫn đường bằng particle

- Có. Particle đánh dấu điểm đích hoặc tạo chuỗi waypoint ngắn từ vị trí người chơi, chỉ hiện với người đang làm quest.
- Config per quest/NPC: bật/tắt, particle type whitelist, màu nếu hỗ trợ, khoảng cách cập nhật, tối đa khoảng cách hiển thị, bán kính objective, thời gian/nhịp, sound và fallback text/compass.
- Mặc định chỉ hiện khi objective đang active, người chơi trong cùng world và chưa tới đích; không broadcast cho cả server.
- Không spawn particle mỗi tick cho mọi người chơi. Dùng scheduled update theo interval giới hạn hoặc PlayerMoveEvent có throttle; dọn task/cache khi quest complete, quit, đổi world, death/disable.
- Nếu đích bị khóa/không có trong world hiện tại, không chỉ sai hướng qua dimension; hiển thị yêu cầu mở cổng trước.
- Khi tới điểm, dừng marker, phát hiệu ứng hoàn tất một lần, cập nhật quest idempotently. Cho người chơi tắt particle trong settings; journal vẫn có chỉ dẫn chữ.
- Chống lộ vị trí bí mật: waypoint chỉ bật khi quest đã mở objective và điều kiện khám phá cho phép.

## 11. Kiến trúc và dữ liệu đề xuất

### 11.1 Bounded modules

```text
rpg/skill/
  SkillDefinitionLoader
  PlayerSkillService
  SkillCastService
  SkillBookListener
  QuickCastMenu
rpg/portal/
  PortalDefinitionLoader
  PortalAdminCommand
  PortalSelectionListener
  PortalTravelService
rpg/npc/
  NpcDefinitionLoader
  NpcRegistry
  NpcInteractionListener
  NpcDialogueService
  NpcAdminCommand
  NpcManagerMenu
rpg/tutorial/
  TutorialWaypointService
```

Không bắt buộc tạo toàn bộ package/class ngay; giữ validation, persistence, domain rules, GUI và listener có trách nhiệm rõ. Wire vào lifecycle plugin, reload và disable có cleanup.

### 11.2 File nội dung

- `skills.yml`: skill ID, class ID, mô tả, rank, cost, cooldown, target/effect allowlisted.
- `skill-books.yml`: item template, lore, class/level restriction, consume policy và source ID.
- `portals/<id>.yml`: mỗi portal endpoint, region, destination, link và access policy.
- `npcs/<id>.yml` hoặc một registry có version: NPC instance/template, vị trí, display name, entity/skin, dialogue/capability IDs.
- `dialogues/*.yml`: node ID, text, choice, next node và quest condition; validate graph/cycle/target.
- `quests/*.yml`: objective/reward; reuse progression SQLite hiện tại.
- Mọi tham số mặc định phải có comment VI/EN nêu công dụng, default, valid range theo quy định dự án.

### 11.3 Dữ liệu SQLite

Các bảng/schema cần thiết kế sau khi đối chiếu version hiện tại:

- `player_skill_progress(uuid, skill_id, rank, unlocked_at)` – duy nhất theo player+skill.
- `player_skill_loadout(uuid, slot, skill_id)` – slot 1..9, unique player+slot; kiểm tra skill đã học.
- Skill points/level-up lưu trong profile hoặc bảng character progression transaction-safe.
- Tái dùng `player_quest_progress`, `player_world_progress`, `player_portal_progress` nếu ngữ nghĩa khớp; không nhân đôi bảng tương đương.
- Migration version mới phải backup, transaction, rollback nếu lỗi; test database cũ có profile/quest/world/portal data.

## 12. Lộ trình có thứ tự và nghiệm thu

### Phase 0 — Chốt hợp đồng thiết kế và kiểm tra API

- Xác nhận danh sách class/race, 3 class đầu, skill names/lore, policy PvP/boss, Q conflict và level curve.
- Xem lại event/persistence hiện có; xác định `level`/`exp` semantics.
- Làm spike packet cho number-key/quick cast và fake player skin; không triển khai gameplay trong spike.
- Chốt storage portal/NPC, permission node, config, reload behavior.

**Nghiệm thu:** tài liệu/fixture có schema mẫu hợp lệ; không có giả định API chưa xác minh; test compatibility ProtocolLib 5.4.0/5.5.0 mismatch đã được giải quyết hoặc tính năng packet bị giới hạn theo fallback.

### Phase 1 — Level-up, point và migration

- Migration SQLite an toàn, điểm chưa tiêu và level/EXP threshold.
- Tích hợp reward EXP quest hiện có, chống reward lặp và xử lý lên nhiều cấp.
- Hồ sơ GUI hiển thị level, thanh EXP và điểm skill còn.

**Nghiệm thu:** profile cũ giữ nguyên; transaction/rollback/restart tests pass; EXP không cộng trùng; cap và overflow được kiểm tra.

### Phase 2 — Skill registry, book và ba class đầu

- Loader/validator `skills.yml`/`skill-books.yml`; PDC skill book; học sách idempotent.
- Implement MVP `swordsman`, `mage`, `sage` theo section 4; test PvP/protection/boss/death.
- GUI Skill Tree, detail, nâng rank bằng point và xác nhận.

**Nghiệm thu:** class sai/skill ID sai bị báo cụ thể; không thể giả sách bằng rename; không mất sách/point khi DB lỗi; từng skill dọn state đúng lifecycle.

### Phase 3 — Quick cast

- Q mở Quick Cast qua drop event có config on/off; hotbar drop conflict được thông báo.
- Quickbar GUI 9 slot, bind/unbind, cooldown/resource/status, click và number key trong GUI.
- Prototype packet độc lập quyết định có làm direct number input ngoài GUI hay không; GUI/click/command vẫn hoạt động nếu packet không tương thích.

**Nghiệm thu:** không mất item khi Q mở menu; không swap item trong GUI; F vẫn mở hub; quickbar không cast skill chưa học/class sai; người chơi có fallback accessible.

### Phase 4 — Portal admin tool và endpoint linking

- Selection rìu gỗ PDC, pos1/pos2, giới hạn diện tích/world/protection.
- Commands create/setspawn/link/unlink/list/info/enable/disable/remove/validate và permissions.
- Loader, storage/backup, GUI quản trị tối thiểu, access conditions và travel guard.

**Nghiệm thu:** chọn vùng không phá block; không duplicate ID/link mồ côi; round-trip reload giữ nguyên; teleport chỉ hoàn tất và ghi progress sau success; không bypass realm/quest/combat.

### Phase 5 — NPC registry và GUI tương tác

- Tạo/lưu nhiều NPC ID, spawn/cleanup/reload, đổi tên/di chuyển, quota.
- Bắt đầu bằng NPC entity Paper; dialogue GUI và capabilities tutorial/quest/trainer/gatekeeper.
- Admin list/editor GUI và commands; merchant/lore keeper/healer trong các phase mở rộng.

**Nghiệm thu:** restart không clone NPC; interaction đúng ID; NPC disabled/missing dialogue không crash; click exploit không nhân reward/item.

### Phase 6 — Tutorial waypoint và quest/story chain

- Tutorial nhiều bước tích hợp skill book, level/point, quick cast, NPC, portal.
- Waypoint particle có throttle, config, per-player visibility, shutdown cleanup và text fallback.
- Liên kết chapter/quest/boss/portal, reward exactly-once.

**Nghiệm thu:** người mới tự hoàn thành trong 10–15 phút theo playtest; không soft-lock nếu bỏ sách/death/reconnect; waypoint dừng đúng lúc; không spam task/particle.

### Phase 7 — Mở rộng class, NPC skin và cân bằng

- Thêm class theo wave và kiểm thử ma trận build.
- Fake-player skin spike thành feature chỉ khi API/ProtocolLib được xác nhận; username/UUID skin, cache, fallback và cleanup.
- Side quests, merchants, lore chains, healer, boss/story integration.

**Nghiệm thu:** không có class áp đảo hoặc skill bỏ qua protection; NPC skin không làm mất entity khi texture fetch lỗi; server staging ổn định.

## 13. GUI và điều hướng tổng thể

- Hub F: Hồ sơ, Kỹ năng, Quickbar, Nhật ký nhiệm vụ, Bản đồ/Cổng, Cốt truyện, Thiết lập.
- `/vnskills` mở thẳng Skill UI; command là fallback, không thay UX GUI.
- Quick Cast riêng với Skill Tree: cast bar chỉ gọi skill; Skill Tree học/nâng/xem; Quickbar menu gán slot.
- Player Portal GUI là giao diện xem/đi portal; admin Portal Manager tạo/sửa/validate.
- NPC Dialogue GUI chỉ mở từ NPC tương ứng; NPC Admin GUI chỉ cho permission admin.
- Mọi GUI revalidate UUID, item/PDC, profile, quest, portal access và quyền lúc click; title không dùng làm định danh.
- Có nút back/close/page, lore trạng thái khóa và lý do; xử lý null DB/config errors bằng thông báo rõ, không success-shaped fallback.

## 14. Quyền và lệnh dự kiến

| Permission dự kiến | Dành cho | Ghi chú |
|---|---|---|
| `vnmine.rpg.skills.use` | Người chơi | Có thể mặc định true; dùng skill đã học |
| `vnmine.rpg.skills.admin` | Admin | Reload/inspect skill runtime nếu thật cần |
| `vnmine.rpg.portal.admin` | Admin | Wand, create/link/edit/remove/validate portal |
| `vnmine.rpg.npc.admin` | Admin | Tạo/đổi tên/di chuyển/skin/despawn NPC |
| `vnmine.rpg.debug` | Developer/admin tin cậy | Debug riêng, mặc định không cấp |

Names/nodes chính thức chỉ chốt khi implementation và `plugin.yml` được sửa. Người chơi không cần permission admin để học skill, mở GUI, làm quest hay dùng portal đã mở. README cần cập nhật cú pháp, permission, ID, cấu hình và ví dụ.

## 15. Kiểm thử và playtest

### Skill/level

- EXP threshold, lên nhiều cấp, point exactly-once, cap, rollback/overflow.
- Sách thật PDC, sách giả rename, class mismatch, already learned, double click, inventory full, restart.
- Skill cast/target/cooldown/scaling/point allocation theo mọi rank.
- Damage attribution, đồng minh/PvP, boss, safe zone, spawn protection, respawn grace.
- Death/logout/world-change/reload/disable dọn projectile, tasks, effects và cooldown.

### Input/GUI

- Q bật/tắt, drop thật khi disabled, drop cancellation, Q trong combat/death/open inventory.
- F/hub và Q không xung đột; number-key, click, shift-click, drag, offhand, bottom inventory, stale holder.
- Protocol packet test đúng client/protocol version; nếu không đạt thì xác minh GUI fallback.

### Portal/NPC/tutorial

- Wand pos1/pos2, vùng quá lớn, cross-world, protected block, permissions.
- Portal ID/link một chiều/hai chiều, missing world/spawn, safe destination, combat/death, teleport failure.
- NPC tạo nhiều ID, quota, rename/skin/move/restart/despawn, duplicate cleanup, entity click exploit.
- Dialogue graph thiếu node/cycle, quest reward lặp, item/reward inventory full, offline/reconnect.
- Waypoint mỗi-player, world mismatch, toggle off, task cleanup và performance khi nhiều người.
- Paper integration test ở `TEST/` nếu user yêu cầu; không commit JAR/server/world/log/cache.

### Playtest

- Người mới hoàn thành tutorial solo, biết mở skill, cast, tiêu point và đi cổng.
- Kiểm tra 3 class MVP ở cùng gear/cấp, sau đó từng wave; đo tỷ lệ skill use, tử vong, cooldown downtime và thời gian hoàn thành.
- Chỉ tăng giới hạn NPC/particle/skill target sau benchmark trên server staging.

## 16. Rủi ro và giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Bắt Q/số không đúng API | Dùng Drop event + GUI số/click; packet là spike có fallback |
| Q chiếm thả vật phẩm | Có config tắt intercept, menu/command thay thế và hướng dẫn |
| ProtocolLib runtime/maven version lệch | Giải quyết mismatch trước packet feature; pin version đã kiểm thử |
| Skill tạo damage/task quá nhiều | Allowlist effect, target cap, throttle, cleanup và stress test |
| Skill point/EXP bị cộng trùng | SQLite transaction, idempotency, integration tests/restart tests |
| Rìu selection phá block hoặc vượt region | Hủy event, PDC, permission, volume/world/protection validation |
| Portal destination unsafe | Validate feet/head, border, world, void, protection, teleport success |
| NPC fake player/skin không ổn định | Tách phase, cache, fallback vanilla entity; không phụ thuộc skin để quest chạy |
| NPC/dialogue/quest tăng scope | Vertical slice 1 NPC guide + trainer + gatekeeper, mở thêm sau |
| Particle gây lag hoặc lộ nội dung | Per-player, interval/throttle, quest gate, giới hạn và cleanup |

## 17. Ưu tiên thực hiện

1. Phase 0: chốt gameplay và spike input/skin; đối chiếu source hiện tại.
2. Phase 1: level, EXP, point, migration, profile GUI.
3. Phase 2: registry/book và 3 class MVP.
4. Phase 3: quick cast Q + GUI/number key/click.
5. Phase 4: portal wand/commands/link và travel an toàn.
6. Phase 5–6: NPC, dialogue, tutorial quest và particle waypoint.
7. Phase 7: mở rộng class, skin player NPC, side story và cân bằng.

## 18. Điều kiện hoàn thành vertical slice

- Người chơi hiện hữu không mất profile/quest/realm unlock khi migration.
- Người chơi đạt level, nhận point đúng một lần, học skill book đúng class và nâng skill bằng GUI.
- Q mở quick cast mà không làm rơi item khi bật; người chơi cast qua number key trong GUI hoặc click; có fallback nếu tắt Q.
- Có tối thiểu ba class với hai skill chủ động + một passive mỗi class; skill không bypass protection/PvP/boss rule.
- Admin dùng rìu gỗ chọn vùng, tạo hai portal ID, đặt destination, link hai chiều và validate; người chơi đi qua theo điều kiện mà không teleport sai.
- Có nhiều NPC ID, ít nhất guide/trainer/gatekeeper; NPC có GUI và đổi display name; NPC không clone sau restart.
- Tutorial dẫn người chơi từ NPC qua học/cast skill, waypoint particle tới mục tiêu và portal đầu; particle có thể tắt và có text fallback.
- README/changelog/config/permissions cập nhật khi tính năng được triển khai; test unit/integration liên quan pass; chưa đánh dấu phase hoàn thành trước khi có bằng chứng.

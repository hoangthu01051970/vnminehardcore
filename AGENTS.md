# Hướng dẫn Agent

## Ngôn ngữ và cách làm việc
- Luôn trao đổi với người dùng bằng tiếng Việt; viết rõ ràng, giải thích ngắn gọn thuật ngữ chuyên môn khi cần.
- Không đoán hành vi của hệ thống. Kiểm tra mã nguồn, cấu hình và tài liệu liên quan trước khi kết luận.
- Tìm nguyên nhân gốc rễ trước khi sửa; không thử sửa ngẫu nhiên. Nếu có nhiều cách, nêu ưu và nhược điểm, chọn cách phù hợp nhất và giải thích lý do.
- Nếu người dùng chỉ yêu cầu phân tích/kế hoạch, chỉ đọc và đề xuất; không sửa file, chạy build/test hay thay đổi trạng thái hệ thống cho đến khi được yêu cầu thực hiện.
- Khi đề xuất thay đổi, nêu rõ nguyên nhân, file/thành phần liên quan, các lựa chọn, file/module sẽ bị ảnh hưởng và hành vi có thể thay đổi.

## Phạm vi thay đổi
- Chỉ sửa các file cần thiết cho yêu cầu; giữ phong cách hiện tại và không thay đổi hành vi không liên quan.
- Ưu tiên giải pháp chính thức, tránh workaround tạm thời nếu có cách sửa đúng gốc.
- Không tự ý clone repository từ xa. Nếu cần mã nguồn ngoài, kiểm tra đường dẫn đã được cấu hình; nếu không có, báo người dùng và xin thống nhất trước khi clone, kèm phương án sao lưu.

## Theo dõi kế hoạch
- Khi hoàn thành một phần công việc thuộc kế hoạch nhiều giai đoạn của dự án, cập nhật `plan.md`: đánh dấu đúng các mục đã hoàn thành, ghi chú kết quả cần thiết và giữ nguyên trạng thái các mục chưa làm.
- Chỉ đánh dấu hoàn thành sau khi đã xác minh kết quả; không tự mở rộng phạm vi hoặc đánh dấu trước các công việc chưa thực hiện.
- Trong báo cáo cuối, nêu phần kế hoạch tiếp theo và hỏi người dùng có muốn tiếp tục không. Chờ xác nhận trước khi bắt đầu phần kế tiếp.
- Với yêu cầu độc lập, không liên quan đến kế hoạch đang theo dõi, không sửa `plan.md` chỉ để tạo cập nhật hình thức.

## Commit, Push và Pull Request
- Sau khi hoàn thành công việc, hỏi người dùng có muốn commit và push thay đổi lên GitHub, đồng thời tạo pull request hay không.
- Chỉ thực hiện khi người dùng đồng ý. Trước khi commit, kiểm tra diff và chỉ đưa các thay đổi thuộc phạm vi công việc vào commit; tạo tiêu đề commit và nội dung mô tả phù hợp với thay đổi.
- Sau khi commit, push nhánh lên remote phù hợp và tạo pull request đến repository/nhánh đích phù hợp với cấu hình remote. Nếu thiếu thông tin hoặc không đủ quyền để xác định repo/nhánh đích, hỏi người dùng trước khi tạo pull request.
- Nếu người dùng từ chối, không commit, push hoặc tạo pull request.

## Cấu hình và tài liệu
- Mọi tính năng mới phải có cấu hình để người quản trị tùy chỉnh mà không cần sửa và biên dịch lại mã Java. Cung cấp giá trị mặc định hợp lý khi khóa cấu hình bị thiếu.
- Mọi tham số trong file cấu hình mặc định phải có chú thích cả tiếng Việt và tiếng Anh, nêu công dụng, giá trị mặc định và giới hạn hợp lệ nếu có. Ví dụ:
  ```yaml
  # [VI] Thời gian cảnh báo trước khi thiên tai xảy ra (giây)
  # [EN] Warning time in seconds before a disaster strikes
  warning-seconds: 180
  ```
- Khi thêm tính năng, cập nhật README với lệnh và cú pháp mới, permission, ID mới, hướng dẫn sử dụng và bảng permission nếu có; bổ sung changelog theo quy ước dự án.

## Báo cáo hoàn thành
Khi người dùng yêu cầu build file jar,chạy build, báo cáo bằng tiếng Việt, ngắn gọn và đủ thông tin:
- Trạng thái build: SUCCESS hoặc FAILED.
- Luôn tăng số hiệu phiên bản khi build chứa tính năng mới theo dạng X.Y.Z: X là phiên bản lớn nhất, Y là khi có thêm chức năng mới, Z là khi sửa lỗi các tính năng có sẵn; nếu không có tính năng mới thì chỉ cập nhật Z khi cần sửa lỗi.
- Thời điểm build thành công; đường dẫn và dung lượng file JAR; SHA256.
- Các file đã sửa và tóm tắt thay đổi.
- không build file jar khi người dùng chưa yêu cầu
function csvData = parseCsvFile(filename)
%UNTITLED2 此处显示有关此函数的摘要
%   此处显示详细说明
% 读取CSV文件
%filename = 'example .csv'; % 替换为你的文件名
data = readcell(filename, 'Delimiter', ',');

% 初始化结构体
fileStruct.Comment = data{2, 2}; % 文件的Comment属性
fileStruct.Segments = struct('Name', {}, 'Comment', {}, 'StartTime', {}, 'SensorList',{}, 'Sensors', {}); % 初始化为一个空结构体数组

segmentIndex = 1; % Segment索引

% 遍历数据行
i = 3;
while i <= size(data,1)  
    % 检查Segment名称
    if i <= size(data, 1) && ischar(data{i, 3}) && startsWith(data{i, 3}, 'Segment') % 如果第三列以"Segment"开头
        % 新建Segment结构体
        segmentName = data{i, 4}; % 获取Segment名称
        newSegment.Name = segmentName;
        
        % 读取Segment属性
        newSegment.Comment = data{i + 1, 4}; % 读取Comment
        newSegment.StartTime = data{i + 2, 4}; % 读取StartTime
        newSegment.SensorList = data{i + 3, 4}; % 初始化Sensors为一个空数组
        
        sensorIndex = 1; % 重置Sensor索引
        newSensor = [] %重置Sensor结构
        i = i + 4; % 跳过Segment属性行
        
        % 处理Sensor
        while i <= size(data, 1) && ischar(data{i, 5}) && startsWith(data{i, 5}, 'Sensor ') % 检查Sensor名称
            disp("Found sensor");
            % 新建Sensor结构体
            sensorName = data{i, 6}; % 获取Sensor名称
            newSensor.Name = sensorName;
            
            % 读取Sensor属性
            newSensor.Gain = data{i + 1, 7}; % 读取Gain
            newSensor.SampleRate = data{i + 2, 7}; % 读取SampleRate
            
            % 读取Data属性
            dataRow = i + 3; % Data行的索引
            if dataRow <= size(data, 1) % 确保不超出边界
                newSensor.Data = data(dataRow, 7:end); % 从第7列开始读取
            else
                newSensor.Data = []; % 如果超出边界，设置为空
            end
            
            % 将新Sensor添加到Segment中
            newSegment.Sensors(sensorIndex) = newSensor; % 将Sensor添加到Segment的Sensors数组
            sensorIndex = sensorIndex + 1; % 增加Sensor索引
            i = i + 4; % 跳过Sensor属性行
        end
        
        % 将新Segment添加到fileStruct中
        fileStruct.Segments(segmentIndex) = newSegment; % 将Segment添加到Segments数组
        segmentIndex = segmentIndex + 1; % 增加Segment索引
        newSegment=[];
    end
end
csvData = fileStruct;
end

